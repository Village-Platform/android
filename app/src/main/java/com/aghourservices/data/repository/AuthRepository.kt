package com.aghourservices.data.repository

import com.aghourservices.data.remote.AghourApi
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.remote.DeleteMeRequest
import com.aghourservices.data.remote.LoginRequest
import com.aghourservices.data.remote.RegisterRequest
import com.aghourservices.data.remote.RegisterUser
import com.aghourservices.data.remote.UpdateMeRequest
import com.aghourservices.data.remote.UpdateMeUser
import com.aghourservices.data.remote.UpdateProfileRequest
import com.aghourservices.data.remote.UpdateProfileUser
import com.aghourservices.data.remote.UserDto
import com.aghourservices.data.remote.safeApiCall
import com.aghourservices.data.session.SessionStore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

class AuthRepository(
    private val api: AghourApi,
    private val session: SessionStore,
    private val deviceRepoProvider: () -> DeviceRepository,
    private val json: Json,
) {
    val currentUser: StateFlow<UserDto?> = session.user

    suspend fun ensureGuestToken(): String? {
        val existingToken = session.token.first()
        val existingUser = session.user.first()
        if (existingUser != null) return existingToken
        if (existingToken != null) return existingToken

        return when (val result = safeApiCall(json) { api.guestToken().data }) {
            is ApiResult.Success -> {
                if (session.user.value == null) {
                    session.saveToken(result.data.token)
                    result.data.token
                } else session.token.value
            }
            is ApiResult.Failure -> null
        }
    }

    suspend fun fetchGuestToken() {
        ensureGuestToken()
    }

    suspend fun login(phone: String, password: String): ApiResult<UserDto> =
        when (val result = safeApiCall(json) { api.login(LoginRequest(phone.trim(), password)).data }) {
            is ApiResult.Success -> {
                session.save(result.data.token, result.data.user)
                syncDeviceToken()
                ApiResult.Success(result.data.user)
            }
            is ApiResult.Failure -> result
        }

    suspend fun register(
        name: String,
        phone: String,
        email: String?,
        password: String,
        passwordConfirmation: String,
    ): ApiResult<UserDto> {
        val body = RegisterRequest(
            RegisterUser(
                name = name.trim(),
                phone = phone.trim(),
                email = email?.trim()?.takeIf { it.isNotEmpty() },
                password = password,
                passwordConfirmation = passwordConfirmation,
            ),
        )
        return when (val result = safeApiCall(json) { api.register(body).data }) {
            is ApiResult.Success -> {
                session.save(result.data.token, result.data.user)
                syncDeviceToken()
                ApiResult.Success(result.data.user)
            }
            is ApiResult.Failure -> result
        }
    }

    /** Refreshes the stored user (role may change after a subscription is approved). */
    suspend fun refreshMe(): ApiResult<UserDto> =
        when (val result = safeApiCall(json) { api.me().data }) {
            is ApiResult.Success -> {
                session.updateUser(result.data)
                result
            }
            is ApiResult.Failure -> result
        }

    /** Name and email (blank email clears it). Updates the stored user on success. */
    suspend fun updateProfile(name: String, email: String): ApiResult<UserDto> =
        storingUser { api.updateProfile(UpdateProfileRequest(UpdateProfileUser(name.trim(), email.trim().ifEmpty { null }))).data }

    /** [jpeg] already scaled down (loadPhotoJpeg); null removes the picture. */
    suspend fun setAvatar(jpeg: ByteArray?): ApiResult<UserDto> =
        storingUser { if (jpeg == null) api.deleteAvatar().data else api.uploadAvatar(jpeg.imagePart("avatar")).data }

    private suspend fun storingUser(block: suspend () -> UserDto): ApiResult<UserDto> =
        safeApiCall(json, block).also { if (it is ApiResult.Success) session.updateUser(it.data) }

    suspend fun changePassword(password: String, confirmation: String): ApiResult<UserDto> =
        safeApiCall(json) { api.updateMe(UpdateMeRequest(UpdateMeUser(password, confirmation))).data }

    /** Deletes the account on the server, then signs out locally. */
    suspend fun deleteAccount(password: String): ApiResult<Unit> =
        when (val result = safeApiCall(json) { api.deleteMe(DeleteMeRequest(password)) }) {
            is ApiResult.Success -> {
                runCatching { deviceRepoProvider().detachDevice() }
                session.clear()
                fetchGuestToken()
                result
            }
            is ApiResult.Failure -> result
        }

    suspend fun logout() {
        runCatching { deviceRepoProvider().detachDevice() }
        session.clear()
        fetchGuestToken()
    }

    private suspend fun syncDeviceToken() {
        runCatching {
            val token = fetchFcmToken()
            if (token != null) {
                deviceRepoProvider().registerDevice(token)
            }
        }
    }

    private suspend fun fetchFcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }
}
