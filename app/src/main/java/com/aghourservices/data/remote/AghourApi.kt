package com.aghourservices.data.remote

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query

/** Rails API under /api/v1 (see aghour_services README). Null query params are omitted. */
interface AghourApi {

    @GET("categories")
    suspend fun categories(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ListResponse<CategoryDto>

    @GET("categories/{id}/firms")
    suspend fun categoryFirms(
        @Path("id") categoryId: Long,
        @Query("q") query: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
    ): ListResponse<FirmDto>

    /** Visible firms, optionally filtered by name and/or category. mine=true -> the signed-in owner's firms. */
    @GET("firms")
    suspend fun firms(
        @Query("q") query: String? = null,
        @Query("category_id") categoryId: Long? = null,
        @Query("mine") mine: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
    ): ListResponse<FirmDto>

    @GET("firms/{id}")
    suspend fun firm(@Path("id") id: Long): ItemResponse<FirmDto>

    @GET("articles")
    suspend fun articles(
        @Query("kind") kind: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
    ): ListResponse<ArticleDto>

    @GET("articles/{id}")
    suspend fun article(@Path("id") id: Long): ItemResponse<ArticleDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ItemResponse<AuthPayload>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): ItemResponse<AuthPayload>

    @POST("auth/guest")
    suspend fun guestToken(): ItemResponse<GuestPayload>

    @GET("me")
    suspend fun me(): ItemResponse<UserDto>

    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateMeRequest): ItemResponse<UserDto>

    @PATCH("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ItemResponse<UserDto>

    /** Multipart part "avatar"; PNG/JPEG/WEBP up to 2MB. */
    @Multipart
    @PUT("me/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): ItemResponse<UserDto>

    @DELETE("me/avatar")
    suspend fun deleteAvatar(): ItemResponse<UserDto>

    /** 204 on success. Plain Unit (not Response<Unit>) so a 422 wrong password throws into safeApiCall. */
    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteMe(@Body body: DeleteMeRequest)

    // ---- Firm owners: plans, payment, subscriptions ----

    @GET("subscription_plans")
    suspend fun plans(): ListResponse<SubscriptionPlanDto>

    /** Feature flags from the dashboard: { data: { features: { "plans": false } } }. Public. */
    @GET("app_config")
    suspend fun appConfig(): ItemResponse<AppConfigDto>

    @GET("payment_methods")
    suspend fun paymentMethods(): DataList<PaymentMethodDto>

    @GET("subscriptions")
    suspend fun subscriptions(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
    ): ListResponse<SubscriptionDto>

    @POST("subscriptions")
    suspend fun createSubscription(@Body body: CreateSubscriptionRequest): ItemResponse<SubscriptionDto>

    // ---- Notifications ----

    @GET("notifications")
    suspend fun notifications(@Query("page") page: Int = 1, @Query("per_page") perPage: Int = 30): ListResponse<NotificationDto>

    @PATCH("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Long): ItemResponse<NotificationDto>

    @PATCH("notifications/read_all")
    suspend fun markAllNotificationsRead(): retrofit2.Response<Unit>

    // ---- Devices & Topics ----

    @PUT("devices/{installation_id}")
    suspend fun registerDevice(
        @Path("installation_id") installationId: String,
        @Body body: RegisterDeviceRequest,
    ): ItemResponse<DeviceDto>

    @POST("devices/{installation_id}/detach")
    suspend fun detachDevice(
        @Path("installation_id") installationId: String,
    ): ItemResponse<DeviceDto>

    @GET("topics")
    suspend fun topics(): DataList<TopicDto>

    @PUT("devices/{device_installation_id}/topics/{key}")
    suspend fun subscribeTopic(
        @Path("device_installation_id") deviceInstallationId: String,
        @Path("key") key: String,
    ): ItemResponse<TopicDto>

    @DELETE("devices/{device_installation_id}/topics/{key}")
    suspend fun unsubscribeTopic(
        @Path("device_installation_id") deviceInstallationId: String,
        @Path("key") key: String,
    ): ItemResponse<TopicDto>

    // ---- Super admin ----
    // Bodies are JsonObject so a cleared field is sent as null (the app's Json drops null properties).

    @POST("firms")
    suspend fun createFirm(@Body body: JsonObject): ItemResponse<FirmDto>

    @PATCH("firms/{id}")
    suspend fun updateFirm(@Path("id") id: Long, @Body body: JsonObject): ItemResponse<FirmDto>

    /** 204 No Content. A Response<Unit> does not throw on 4xx; check isSuccessful. */
    @DELETE("firms/{id}")
    suspend fun deleteFirm(@Path("id") id: Long): Response<Unit>

    // Images of a firm or an article: one per request, at most five (422 past that).

    @Multipart
    @POST("firms/{id}/attachments")
    suspend fun addFirmAttachment(@Path("id") id: Long, @Part file: MultipartBody.Part): ItemResponse<FirmDto>

    @DELETE("firms/{id}/attachments/{attachmentId}")
    suspend fun deleteFirmAttachment(@Path("id") id: Long, @Path("attachmentId") attachmentId: Long): ItemResponse<FirmDto>

    @Multipart
    @POST("articles/{id}/attachments")
    suspend fun addArticleAttachment(@Path("id") id: Long, @Part file: MultipartBody.Part): ItemResponse<ArticleDto>

    @DELETE("articles/{id}/attachments/{attachmentId}")
    suspend fun deleteArticleAttachment(@Path("id") id: Long, @Path("attachmentId") attachmentId: Long): ItemResponse<ArticleDto>

    @GET("categories/{id}")
    suspend fun category(@Path("id") id: Long): ItemResponse<CategoryDto>

    /** Multipart because of the optional icon image (category[icon_image]). */
    @Multipart
    @POST("categories")
    suspend fun createCategory(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part icon: MultipartBody.Part?,
    ): ItemResponse<CategoryDto>

    @Multipart
    @PATCH("categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: Long,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part icon: MultipartBody.Part?,
    ): ItemResponse<CategoryDto>

    @DELETE("categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Long): Response<Unit>

    @POST("articles")
    suspend fun createArticle(@Body body: JsonObject): ItemResponse<ArticleDto>

    @PATCH("articles/{id}")
    suspend fun updateArticle(@Path("id") id: Long, @Body body: JsonObject): ItemResponse<ArticleDto>

    @DELETE("articles/{id}")
    suspend fun deleteArticle(@Path("id") id: Long): Response<Unit>

    @GET("subscription_plans/{id}")
    suspend fun plan(@Path("id") id: Long): ItemResponse<SubscriptionPlanDto>

    @POST("subscription_plans")
    suspend fun createPlan(@Body body: JsonObject): ItemResponse<SubscriptionPlanDto>

    @PATCH("subscription_plans/{id}")
    suspend fun updatePlan(@Path("id") id: Long, @Body body: JsonObject): ItemResponse<SubscriptionPlanDto>

    @DELETE("subscription_plans/{id}")
    suspend fun deletePlan(@Path("id") id: Long): Response<Unit>
}
