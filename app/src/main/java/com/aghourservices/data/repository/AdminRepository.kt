package com.aghourservices.data.repository

import com.aghourservices.data.analytics.AnalyticsEvent
import com.aghourservices.data.analytics.AnalyticsEvent.Action
import com.aghourservices.data.analytics.AnalyticsEvent.Entity
import com.aghourservices.data.analytics.AnalyticsService
import com.aghourservices.data.remote.AghourApi
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.remote.ArticleDto
import com.aghourservices.data.remote.CategoryDto
import com.aghourservices.data.remote.FirmDto
import com.aghourservices.data.remote.SubscriptionPlanDto
import com.aghourservices.data.remote.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Firm fields from the form. Blank text is sent as null so a field can be cleared.
 * [status] is null for owners: only the super admin may set it, so it is not sent at all.
 */
data class FirmInput(
    val categoryId: Long,
    val name: String,
    val description: String,
    val phone: String,
    val whatsapp: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val workingHours: String,
    val status: String?,
)

/** [iconPng] is a new image to upload (already scaled down); null keeps the current one. */
data class CategoryInput(
    val name: String,
    val description: String,
    val position: Int,
    val active: Boolean,
    val iconPng: ByteArray?,
)

/** [publishedAt] null = draft (hidden from everyone but the super admin). */
data class ArticleInput(val title: String, val body: String, val kind: String, val publishedAt: String?)

data class PlanInput(
    val name: String,
    val description: String,
    val priceCents: Int,
    val durationDays: Int,
    val maxFirms: Int,
    val position: Int,
    val active: Boolean,
)

/**
 * Writes from the management screens: the super admin's, and firm owners' for their own firms.
 * The server's policies decide what is allowed; this class only sends.
 * [changes] goes up after every successful write, so any screen showing server data can reload.
 */
class AdminRepository(
    private val api: AghourApi,
    private val json: Json,
    private val catalog: CatalogRepository,
    private val analytics: AnalyticsService,
) {

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    // ---- Firms ----

    suspend fun createFirm(input: FirmInput): ApiResult<FirmDto> =
        write(Entity.Firm, Action.Create) { api.createFirm(input.toBody()).data }

    suspend fun updateFirm(id: Long, input: FirmInput): ApiResult<FirmDto> =
        write(Entity.Firm, Action.Update) { api.updateFirm(id, input.toBody()).data }

    /** Approve (active) or suspend without opening the form. */
    suspend fun setFirmStatus(id: Long, status: String): ApiResult<FirmDto> =
        write(Entity.Firm, if (status == "active") Action.Approve else Action.Suspend) { api.updateFirm(id, buildJsonObject { put("firm", buildJsonObject { put("status", status) }) }).data }

    suspend fun deleteFirm(id: Long): ApiResult<Unit> =
        write(Entity.Firm, Action.Delete) { api.deleteFirm(id).orThrow() }

    suspend fun addFirmAttachment(id: Long, jpeg: ByteArray): ApiResult<FirmDto> =
        write(Entity.Firm, Action.Update) { api.addFirmAttachment(id, jpeg.imagePart()).data }

    suspend fun deleteFirmAttachment(id: Long, attachmentId: Long): ApiResult<FirmDto> =
        write(Entity.Firm, Action.Update) { api.deleteFirmAttachment(id, attachmentId).data }

    // ---- Categories ----

    suspend fun category(id: Long): ApiResult<CategoryDto> =
        safeApiCall(json) { api.category(id).data }

    suspend fun createCategory(input: CategoryInput): ApiResult<CategoryDto> =
        write(Entity.Category, Action.Create, categories = true) { api.createCategory(input.fields(), input.iconPart()).data }

    suspend fun updateCategory(id: Long, input: CategoryInput): ApiResult<CategoryDto> =
        write(Entity.Category, Action.Update, categories = true) { api.updateCategory(id, input.fields(), input.iconPart()).data }

    /** Refused by the server while firms use the category (its message says so). */
    suspend fun deleteCategory(id: Long): ApiResult<Unit> =
        write(Entity.Category, Action.Delete, categories = true) { api.deleteCategory(id).orThrow() }

    // ---- Articles ----

    suspend fun createArticle(input: ArticleInput): ApiResult<ArticleDto> =
        write(Entity.Article, Action.Create) { api.createArticle(input.toBody()).data }

    suspend fun updateArticle(id: Long, input: ArticleInput): ApiResult<ArticleDto> =
        write(Entity.Article, Action.Update) { api.updateArticle(id, input.toBody()).data }

    suspend fun deleteArticle(id: Long): ApiResult<Unit> =
        write(Entity.Article, Action.Delete) { api.deleteArticle(id).orThrow() }

    suspend fun addArticleAttachment(id: Long, jpeg: ByteArray): ApiResult<ArticleDto> =
        write(Entity.Article, Action.Update) { api.addArticleAttachment(id, jpeg.imagePart()).data }

    suspend fun deleteArticleAttachment(id: Long, attachmentId: Long): ApiResult<ArticleDto> =
        write(Entity.Article, Action.Update) { api.deleteArticleAttachment(id, attachmentId).data }

    // ---- Subscription plans ----

    suspend fun plan(id: Long): ApiResult<SubscriptionPlanDto> =
        safeApiCall(json) { api.plan(id).data }

    suspend fun createPlan(input: PlanInput): ApiResult<SubscriptionPlanDto> =
        write(Entity.Plan, Action.Create) { api.createPlan(input.toBody()).data }

    suspend fun updatePlan(id: Long, input: PlanInput): ApiResult<SubscriptionPlanDto> =
        write(Entity.Plan, Action.Update) { api.updatePlan(id, input.toBody()).data }

    /** Refused by the server once the plan has subscriptions; deactivate it instead. */
    suspend fun deletePlan(id: Long): ApiResult<Unit> =
        write(Entity.Plan, Action.Delete) { api.deletePlan(id).orThrow() }

    /**
     * Runs a write; on success tells the screens to reload (and drops the category cache if needed)
     * and records it for analytics.
     */
    private suspend fun <T> write(
        entity: Entity,
        action: Action,
        categories: Boolean = false,
        block: suspend () -> T,
    ): ApiResult<T> =
        safeApiCall(json, block).also {
            if (it is ApiResult.Success) {
                if (categories) catalog.forgetCategories()
                _changes.update { n -> n + 1 }
                analytics.log(AnalyticsEvent.ManageContent(entity, action))
            }
        }
}

private fun FirmInput.toBody(): JsonObject = buildJsonObject {
    put(
        "firm",
        buildJsonObject {
            put("category_id", categoryId)
            put("name", name.trim())
            put("description", description.trim().ifEmpty { null })
            put("phone", phone.trim().ifEmpty { null })
            put("whatsapp", whatsapp.trim().ifEmpty { null })
            put("address", address.trim().ifEmpty { null })
            put("latitude", latitude)
            put("longitude", longitude)
            put("working_hours", workingHours.trim().ifEmpty { null })
            status?.let { put("status", it) }
        },
    )
}

private fun ArticleInput.toBody(): JsonObject = buildJsonObject {
    put(
        "article",
        buildJsonObject {
            put("title", title.trim())
            put("body", body.trim())
            put("kind", kind)
            put("published_at", publishedAt)
        },
    )
}

private fun PlanInput.toBody(): JsonObject = buildJsonObject {
    put(
        "subscription_plan",
        buildJsonObject {
            put("name", name.trim())
            put("description", description.trim().ifEmpty { null })
            put("price_cents", priceCents)
            put("duration_days", durationDays)
            put("max_firms", maxFirms)
            put("position", position)
            put("active", active)
        },
    )
}

private val TextPlain = "text/plain".toMediaType()

/** Multipart can't carry null, so a cleared description is sent as "" (the apps treat blank as empty). */
private fun CategoryInput.fields(): Map<String, RequestBody> = mapOf(
    "category[name]" to name.trim().toRequestBody(TextPlain),
    "category[description]" to description.trim().toRequestBody(TextPlain),
    "category[position]" to position.toString().toRequestBody(TextPlain),
    "category[active]" to active.toString().toRequestBody(TextPlain),
)

private fun CategoryInput.iconPart(): MultipartBody.Part? = iconPng?.let {
    MultipartBody.Part.createFormData("category[icon_image]", "icon.png", it.toRequestBody("image/png".toMediaType()))
}

/** One picked image (already scaled down and re-encoded by loadPhotoJpeg). */
internal fun ByteArray.imagePart(name: String = "file"): MultipartBody.Part =
    MultipartBody.Part.createFormData(name, "image.jpg", toRequestBody("image/jpeg".toMediaType()))

/** Turns a non-2xx Response into the HttpException that safeApiCall knows how to read. */
private fun Response<Unit>.orThrow() {
    if (!isSuccessful) throw HttpException(this)
}
