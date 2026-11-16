package com.aghourservices.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---- Envelopes (match the Rails API: { data, meta } and { error }) ----

@Serializable
data class ListResponse<T>(val data: List<T>, val meta: PageMeta)

@Serializable
data class ItemResponse<T>(val data: T)

/** Lists that are not paginated (no meta), e.g. payment methods. */
@Serializable
data class DataList<T>(val data: List<T>)

@Serializable
data class PageMeta(
    val page: Int,
    @SerialName("per_page") val perPage: Int,
    val total: Int,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

/** GET app_config. Unknown flags are ignored; a missing flag means off. */
@Serializable
data class AppConfigDto(val features: Map<String, Boolean> = emptyMap())

@Serializable
data class ErrorEnvelope(val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: JsonObject? = null,
)

// ---- Resources ----

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    /** Category image (relative to the API host, see categoryIconUrl); null = show the app logo. */
    @SerialName("icon_url") val iconUrl: String? = null,
    val position: Int = 0,
    /** Only a super admin receives inactive categories. */
    val active: Boolean = true,
)

@Serializable
data class CategoryRefDto(
    val id: Long,
    val name: String,
    /** Same key as CategoryDto.icon; optional so the app works before/after the API includes it. */
    val icon: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
)

@Serializable
data class FirmDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("working_hours") val workingHours: String? = null,
    val status: String,
    val category: CategoryRefDto,
    /** Optional so the app works with older API responses; null hides the owner controls. */
    @SerialName("owner_id") val ownerId: Long? = null,
    /** What the signed-in user may do, from the server's FirmPolicy. null on older API versions. */
    val can: FirmCanDto? = null,
    /** Up to five images (issue #169). Empty on older API versions. */
    val attachments: List<AttachmentDto> = emptyList(),
)

/** An image of a firm or an article. [url] is relative to the API host (resolve with [apiAssetUrl]). */
@Serializable
data class AttachmentDto(
    val id: Long,
    val url: String,
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
data class FirmCanDto(val update: Boolean = false, val destroy: Boolean = false, val moderate: Boolean = false)

@Serializable
data class AuthorDto(val id: Long, val name: String)

@Serializable
data class ArticleDto(
    val id: Long,
    val title: String,
    val body: String,
    val kind: String,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("review_status") val reviewStatus: String? = null,
    val author: AuthorDto,
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val phone: String,
    val email: String? = null,
    val role: String,
    /** Relative to the API host; null = no picture (the letter avatar is shown). */
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * Shows the admin controls. Only a hint for the UI: the server's policies are what actually allow
 * or refuse each request, so a stale role just ends in an error message.
 */
val UserDto?.isSuperAdmin: Boolean get() = this?.role == "super_admin"

/** Mirrors User#can_manage_firms? on the server: firm admins and the super admin may add firms. */
val UserDto?.canManageFirms: Boolean get() = this != null
val UserDto?.canManageArticles: Boolean get() = this != null

@Serializable
data class AuthPayload(val token: String, val user: UserDto)

@Serializable
data class SubscriptionPlanDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("duration_days") val durationDays: Int,
    @SerialName("max_firms") val maxFirms: Int,
    val position: Int = 0,
    /** Only a super admin receives inactive plans. */
    val active: Boolean = true,
)

@Serializable
data class PaymentMethodDto(val key: String, val name: String, val account: String? = null)

@Serializable
data class PlanRefDto(
    val id: Long,
    val name: String,
    @SerialName("duration_days") val durationDays: Int,
    @SerialName("max_firms") val maxFirms: Int,
)

@Serializable
data class SubscriptionDto(
    val id: Long,
    val status: String,
    /** pending / rejected / scheduled / current / expired */
    val state: String,
    val plan: PlanRefDto,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("amount_cents") val amountCents: Int,
    @SerialName("transaction_reference") val transactionReference: String,
    @SerialName("days_left") val daysLeft: Int? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NotificationDto(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val data: JsonObject? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ---- Requests ----

@Serializable
data class LoginRequest(val phone: String, val password: String)

@Serializable
data class RegisterRequest(val user: RegisterUser)

@Serializable
data class RegisterUser(
    val name: String,
    val phone: String,
    val email: String? = null,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

@Serializable
data class CreateSubscriptionRequest(val subscription: NewSubscription)

@Serializable
data class NewSubscription(
    @SerialName("subscription_plan_id") val planId: Long,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("transaction_reference") val transactionReference: String,
    @SerialName("payer_phone") val payerPhone: String? = null,
)

@Serializable
data class UpdateMeRequest(val user: UpdateMeUser)

@Serializable
data class UpdateMeUser(
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

/** PATCH /me with the profile fields; email null clears it. */
@Serializable
data class UpdateProfileRequest(val user: UpdateProfileUser)

@Serializable
data class UpdateProfileUser(val name: String, val email: String?)

@Serializable
data class DeleteMeRequest(val password: String)

@Serializable
data class TopicDto(
    val key: String,
    val name: String,
    val description: String? = null,
    val subscribed: Boolean,
)

@Serializable
data class DeviceDto(
    @SerialName("installation_id") val installationId: String,
    val token: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    val locale: String? = null,
)

@Serializable
data class RegisterDeviceRequest(
    val token: String,
    val locale: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    val platform: String? = null,
)

@Serializable
data class GuestPayload(val token: String)
