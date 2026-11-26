package com.aghourservices.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.remote.ArticleDto
import com.aghourservices.data.remote.CategoryDto
import com.aghourservices.data.remote.FirmDto
import com.aghourservices.data.repository.CatalogRepository
import com.aghourservices.data.repository.OwnerRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val categories: List<CategoryDto> = emptyList(),
    val featuredFirms: List<FirmDto> = emptyList(),
    val latestNews: List<ArticleDto> = emptyList(),
    /** Unread notifications for the bell; 0 for guests. */
    val unreadCount: Int = 0,
    val error: String? = null,
)

class HomeViewModel(
    private val catalog: CatalogRepository,
    private val owner: OwnerRepository,
    adminChanges: StateFlow<Int>,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
        // A super admin changed a category, firm or article shown here.
        viewModelScope.launch { adminChanges.drop(1).collect { refresh() } }
    }

    /** First load shows skeletons; pull-to-refresh keeps the current content visible. */
    fun load(refresh: Boolean = false) {
        _state.update { it.copy(loading = !refresh && it.categories.isEmpty(), refreshing = refresh, error = null) }
        viewModelScope.launch {
            val categories = async { catalog.categories(refresh) }
            val firms = async { catalog.featuredFirms(count = 4) }
            val news = async { catalog.articles(page = 1, perPage = 3) }

            when (val result = categories.await()) {
                is ApiResult.Success -> _state.update { it.copy(categories = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
            // Featured firms and news are bonus sections: the page still works if they fail.
            (firms.await() as? ApiResult.Success)?.let { r -> _state.update { it.copy(featuredFirms = r.data.data) } }
            (news.await() as? ApiResult.Success)?.let { r -> _state.update { it.copy(latestNews = r.data.data) } }
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun refresh() = load(refresh = true)

    /** Called each time Home is shown while signed in (notifications may have been read elsewhere). */
    fun loadUnread() {
        viewModelScope.launch {
            (owner.notifications(page = 1, perPage = 1) as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(unreadCount = r.data.meta.unreadCount) }
            }
        }
    }

    fun clearUnread() = _state.update { it.copy(unreadCount = 0) }
}
