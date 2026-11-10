package com.aghourservices.ui.news

import com.aghourservices.data.analytics.AnalyticsEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aghourservices.R
import com.aghourservices.data.remote.ArticleDto
import com.aghourservices.ui.components.AttachmentsGallery
import com.aghourservices.ui.components.Badge
import com.aghourservices.ui.components.BadgeTone
import com.aghourservices.ui.components.FeaturedNewsCard
import com.aghourservices.ui.components.FilterChipsRow
import com.aghourservices.ui.components.MessageBox
import com.aghourservices.ui.components.NewsRow
import com.aghourservices.ui.components.OverlayIconButton
import com.aghourservices.ui.components.ScreenTitle
import com.aghourservices.ui.components.SheetLayout
import com.aghourservices.ui.components.SkeletonRows
import com.aghourservices.ui.components.appContainer
import com.aghourservices.ui.components.asHeading
import com.aghourservices.ui.components.formatDate
import com.aghourservices.ui.components.kindLabel
import com.aghourservices.ui.components.kindTone
import com.aghourservices.ui.components.shareText
import com.aghourservices.ui.theme.Aghour

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(onArticleClick: (ArticleDto) -> Unit) {
    val container = appContainer()
    val vm: NewsViewModel = viewModel { NewsViewModel(container.catalog, container.admin.changes, container.analytics) }
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val kinds = remember { listOf<String?>(null, "news", "announcement", "article") }
    val pagerState = rememberPagerState(pageCount = { kinds.size })

    val targetIndex = kinds.indexOf(state.kind).coerceAtLeast(0)
    LaunchedEffect(targetIndex) {
        if (pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        val selectedKind = kinds.getOrNull(pagerState.settledPage)
        if (selectedKind != state.kind) {
            vm.selectKind(selectedKind)
        }
    }

    val reachedEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(reachedEnd, state.canLoadMore) { if (reachedEnd && state.canLoadMore) vm.loadMore() }
    val c = Aghour.colors

    Column(Modifier.fillMaxSize().background(c.page)) {
        ScreenTitle(stringResource(R.string.news_title))
        FilterChipsRow(
            options = listOf<Pair<String?, String>>(
                null to stringResource(R.string.all),
                "news" to stringResource(R.string.filter_news),
                "announcement" to stringResource(R.string.filter_announcements),
                "article" to stringResource(R.string.filter_articles),
            ),
            selected = state.kind,
            onSelect = vm::selectKind,
        )
        Spacer(Modifier.height(10.dp))

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { _ ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> SkeletonRows(modifier = Modifier.padding(16.dp))
                    state.error != null && state.articles.isEmpty() -> MessageBox(state.error!!, onRetry = vm::reload)
                    state.articles.isEmpty() -> MessageBox(stringResource(R.string.no_news))
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(state.articles, key = { _, a -> a.id }) { index, article ->
                            if (index == 0) {
                                FeaturedNewsCard(article, stringResource(R.string.latest), onClick = { onArticleClick(article) })
                            } else {
                                NewsRow(article, onClick = { onArticleClick(article) })
                            }
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

/** [onEdit] is null for everyone but the super admin (decided in AppNavigation). */
@Composable
fun ArticleScreen(articleId: Long, onBack: () -> Unit, onEdit: (() -> Unit)? = null) {
    val container = appContainer()
    val vm: ArticleViewModel = viewModel { ArticleViewModel(container.catalog, articleId, container.admin.changes, container.analytics) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val article = state.article
    val c = Aghour.colors

    if (article == null) {
        Column(Modifier.fillMaxSize().background(c.page)) {
            ScreenTitle(title = stringResource(R.string.tab_news), onBack = onBack)
            if (state.loading) SkeletonRows(2, Modifier.padding(16.dp)) else MessageBox(state.error ?: "", onRetry = vm::load)
        }
        return
    }

    SheetLayout(
        sheetPadding = PaddingValues(20.dp),
        header = {
            Box(Modifier.fillMaxWidth().height(190.dp)) {
                Image(
                    painter = painterResource(R.drawable.brand_scene),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OverlayIconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onEdit != null) {
                            OverlayIconButton(onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit), tint = Color.White) }
                        }
                        OverlayIconButton({
                            container.analytics.log(AnalyticsEvent.Share("article", article.id))
                            context.shareText("${article.title}\n\n${article.body}")
                        }) {
                            Icon(Icons.Filled.Share, stringResource(R.string.share), tint = Color.White)
                        }
                    }
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(kindLabel(article.kind), kindTone(article.kind))
            Spacer(Modifier.width(8.dp))
            // Drafts reach only the super admin.
            if (article.publishedAt == null) {
                Badge(stringResource(R.string.draft), BadgeTone.Gray)
                Spacer(Modifier.width(8.dp))
            }
            Text(formatDate(article.publishedAt), style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
        Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = c.ink, modifier = Modifier.asHeading())
        Text(article.author.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.goldInk)
        AttachmentsGallery(article.attachments)
        Text(article.body, style = MaterialTheme.typography.bodyLarge, color = c.ink)
    }
}
