package com.aghourservices.data.repository

import com.aghourservices.data.remote.AghourApi
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.remote.ArticleDto
import com.aghourservices.data.remote.CategoryDto
import com.aghourservices.data.remote.FirmDto
import com.aghourservices.data.remote.ListResponse
import com.aghourservices.data.remote.safeApiCall
import kotlinx.serialization.json.Json

/** Public browsing data: categories, firms and news. */
class CatalogRepository(private val api: AghourApi, private val json: Json) {

    @Volatile private var cachedCategories: List<CategoryDto>? = null

    /**
     * Categories (with their icon keys) are needed by almost every screen to draw icons, and they
     * rarely change, so they are kept in memory for the app's lifetime. [refresh] re-fetches them.
     */
    suspend fun categories(refresh: Boolean = false): ApiResult<List<CategoryDto>> {
        cachedCategories?.takeIf { !refresh }?.let { return ApiResult.Success(it) }
        return safeApiCall(json) { api.categories().data }
            .also { if (it is ApiResult.Success) cachedCategories = it.data }
    }

    /** A super admin changed a category: the next [categories] call fetches again. */
    fun forgetCategories() {
        cachedCategories = null
    }

    suspend fun categoryFirms(categoryId: Long, query: String?, page: Int): ApiResult<ListResponse<FirmDto>> =
        safeApiCall(json) { api.categoryFirms(categoryId, query?.takeIf { it.isNotBlank() }, page) }

    /** Explore: all visible firms, filtered by name and/or category. */
    suspend fun firms(query: String?, categoryId: Long?, page: Int, perPage: Int = 20): ApiResult<ListResponse<FirmDto>> =
        safeApiCall(json) {
            api.firms(query = query?.trim()?.takeIf { it.isNotEmpty() }, categoryId = categoryId, page = page, perPage = perPage)
        }

    /** Home's featured section: a random sample of visible firms, different on every load. Not pageable. */
    suspend fun featuredFirms(count: Int): ApiResult<ListResponse<FirmDto>> =
        safeApiCall(json) { api.firms(page = 1, perPage = count, sort = "random") }

    suspend fun firm(id: Long): ApiResult<FirmDto> =
        safeApiCall(json) { api.firm(id).data }

    suspend fun articles(page: Int, perPage: Int = 20, kind: String? = null): ApiResult<ListResponse<ArticleDto>> =
        safeApiCall(json) { api.articles(kind = kind, page = page, perPage = perPage) }

    suspend fun article(id: Long): ApiResult<ArticleDto> =
        safeApiCall(json) { api.article(id).data }
}
