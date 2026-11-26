package com.aghourservices.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aghourservices.R
import com.aghourservices.data.remote.FirmDto
import com.aghourservices.ui.components.FilterChipsRow
import com.aghourservices.ui.components.FirmRow
import com.aghourservices.ui.components.MessageBox
import com.aghourservices.ui.components.MutedText
import com.aghourservices.ui.components.SkeletonRows
import com.aghourservices.ui.components.StatusBarIcons
import com.aghourservices.ui.components.appContainer
import com.aghourservices.ui.components.categoryIconUrl
import com.aghourservices.ui.components.firmIconUrl
import com.aghourservices.ui.theme.Aghour

/** Pager key for the "all" page (category id null); real ids are positive. */
private const val ALL_PAGE_KEY = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onFirmClick: (FirmDto) -> Unit, bottomClearance: androidx.compose.ui.unit.Dp = 0.dp) {
    val container = appContainer()
    val vm: ExploreViewModel = viewModel { ExploreViewModel(container.catalog, container.exploreCategory, container.admin.changes, container.analytics) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val c = Aghour.colors
    StatusBarIcons()

    val categoryIds = remember(state.categories) { listOf<Long?>(null) + state.categories.map { it.id } }
    val pagerState = rememberPagerState(pageCount = { categoryIds.size })

    var isProgrammaticScroll by remember { mutableStateOf(false) }

    val targetIndex = categoryIds.indexOf(state.categoryId).coerceAtLeast(0)
    LaunchedEffect(targetIndex) {
        if (pagerState.currentPage != targetIndex) {
            isProgrammaticScroll = true
            pagerState.animateScrollToPage(targetIndex)
            isProgrammaticScroll = false
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        // Before categories load there is only the "all" page; settling on it must not wipe the
        // category picked on Home.
        if (isProgrammaticScroll || state.categories.isEmpty()) return@LaunchedEffect
        val selectedId = categoryIds.getOrNull(pagerState.settledPage)
        if (selectedId != state.categoryId) {
            vm.selectCategory(selectedId)
        }
    }

    Column(Modifier.fillMaxSize().background(c.page)) {
        Column(Modifier.statusBarsPadding().padding(top = 12.dp, bottom = 8.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.explore_hint), color = c.muted) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = c.muted) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = null, tint = c.muted) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.surface2,
                    unfocusedContainerColor = c.surface2,
                    focusedTextColor = c.ink,
                    unfocusedTextColor = c.ink,
                    focusedBorderColor = c.gold,
                    unfocusedBorderColor = c.line,
                    cursorColor = c.gold,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.reload() }),
            )
            Spacer(Modifier.height(12.dp))
            val allLabel = stringResource(R.string.all)
            FilterChipsRow(
                options = listOf<Pair<Long?, String>>(null to allLabel) + state.categories.map { it.id to it.name },
                selected = state.categoryId,
                onSelect = vm::selectCategory,
                // "الكل" gets a grid icon; categories show their API image, or the app logo without one.
                icons = mapOf<Long?, ImageVector?>(null to Icons.Outlined.Apps) + state.categories.associate { it.id to null },
                imageUrls = state.categories.mapNotNull { category -> categoryIconUrl(category.iconUrl)?.let { category.id to it } }.toMap(),
            )
            if (!state.loading && state.error == null) {
                MutedText(stringResource(R.string.results_count, state.total), Modifier.padding(start = 18.dp, top = 8.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> categoryIds.getOrNull(page) ?: ALL_PAGE_KEY },
        ) { page ->
            // One scroll state per page. A LazyListState can drive only one LazyColumn: sharing it
            // across pages (they all compose while the pager animates to a category picked on Home)
            // left it bound to a disposed list, so scrolling and load-more stopped working.
            val listState = rememberLazyListState()
            // The VM holds firms for the selected category only; other pages show a skeleton.
            val isCurrent = categoryIds.getOrNull(page) == state.categoryId
            if (isCurrent) {
                val reachedEnd by remember(listState) {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
                        last >= info.totalItemsCount - 3
                    }
                }
                LaunchedEffect(reachedEnd, state.canLoadMore) { if (reachedEnd && state.canLoadMore) vm.loadMore() }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                when {
                    !isCurrent || state.loading -> SkeletonRows(modifier = Modifier.padding(16.dp))
                    state.error != null && state.firms.isEmpty() -> MessageBox(state.error!!, onRetry = vm::reload)
                    state.firms.isEmpty() -> MessageBox(stringResource(R.string.no_firms))
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottomClearance),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.firms, key = { it.id }) { firm ->
                            FirmRow(firm, onClick = { onFirmClick(firm) }, iconUrl = firmIconUrl(firm, state.categories))
                        }
                        if (state.loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp), color = c.gold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
