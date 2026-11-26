package com.aghourservices.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aghourservices.R
import com.aghourservices.data.remote.ArticleDto
import com.aghourservices.data.remote.CategoryDto
import com.aghourservices.data.remote.FirmDto
import com.aghourservices.ui.components.BrandMark
import com.aghourservices.ui.components.FirmRow
import com.aghourservices.ui.components.IconTile
import com.aghourservices.ui.components.MessageBox
import com.aghourservices.ui.components.NewsRow
import com.aghourservices.ui.components.SearchPill
import com.aghourservices.ui.components.SectionHeader
import com.aghourservices.ui.components.SkeletonRows
import com.aghourservices.ui.components.StatusBarIcons
import com.aghourservices.ui.components.appContainer
import com.aghourservices.ui.components.categoryIconUrl
import com.aghourservices.ui.components.firmIconUrl
import com.aghourservices.ui.components.greeting
import com.aghourservices.ui.components.shimmer
import com.aghourservices.ui.theme.Aghour

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCategoryClick: (CategoryDto) -> Unit,
    onExplore: () -> Unit,
    onFirmClick: (FirmDto) -> Unit,
    onArticleClick: (ArticleDto) -> Unit,
    onSeeAllNews: () -> Unit,
    /** Extra space under the content for the floating create button. */
    bottomClearance: androidx.compose.ui.unit.Dp = 0.dp,
    /** null while plans are switched off: the banner stays, but is not a link. */
    onAddFirm: (() -> Unit)?,
    onNotifications: () -> Unit,
) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container.catalog, container.owner, container.admin.changes) }
    val state by vm.state.collectAsStateWithLifecycle()
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val c = Aghour.colors
    StatusBarIcons()

    // Refresh the bell each time Home comes back (e.g. after reading notifications).
    LifecycleResumeEffect(Unit) {
        vm.loadUnread()
        onPauseOrDispose {}
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { vm.refresh(); vm.loadUnread() },
        modifier = Modifier.fillMaxSize().background(c.page),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 24.dp + bottomClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Greeting, bell and logo
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), color = c.muted, style = MaterialTheme.typography.labelMedium)
                        Text(
                            user?.name?.let { "${it.substringBefore(' ')} 👋" } ?: stringResource(R.string.hey_guest),
                            color = c.ink,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    BellButton(state.unreadCount, onNotifications)
                    Spacer(Modifier.width(8.dp))
                    BrandMark(Modifier.size(40.dp))
                }
                Spacer(Modifier.height(12.dp))
                SearchPill(stringResource(R.string.search_hint), onClick = onExplore)
            }

            // Category shortcuts
            when {
                state.loading -> CategoriesSkeleton()
                state.error != null && state.categories.isEmpty() -> MessageBox(state.error!!, onRetry = { vm.load() })
                else -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.categories, key = { it.id }) { category ->
                        Column(
                            Modifier.width(62.dp).clip(RoundedCornerShape(12.dp)).clickable { onCategoryClick(category) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconTile(categoryIconUrl(category.iconUrl), size = 50.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                category.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = c.ink,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Cover banner -> "add your firm" (plans), or just the village artwork while plans are off.
            Image(
                painter = painterResource(R.drawable.cover_banner),
                contentDescription = if (onAddFirm != null) stringResource(R.string.add_your_firm) else null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (onAddFirm != null) Modifier.clickable(onClick = onAddFirm) else Modifier),
            )

            if (state.featuredFirms.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(R.string.featured_firms), stringResource(R.string.see_all), onExplore)
                    state.featuredFirms.forEach { firm ->
                        FirmRow(firm, onClick = { onFirmClick(firm) }, iconUrl = firmIconUrl(firm, state.categories))
                    }
                }
            }

            if (state.latestNews.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(R.string.section_latest_news), stringResource(R.string.see_all), onSeeAllNews)
                    state.latestNews.forEach { article -> NewsRow(article, onClick = { onArticleClick(article) }) }
                }
            }

            if (state.loading) SkeletonRows(3, Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** Bell tile with a clean, round unread counter badge. */
@Composable
private fun BellButton(unread: Int, onClick: () -> Unit) {
    val c = Aghour.colors
    Box(modifier = Modifier.padding(top = 4.dp, end = 4.dp)) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surface2)
                .border(1.dp, c.line, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.notifications), tint = c.ink, modifier = Modifier.size(22.dp))
        }
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(c.bad)
                    .border(1.5.dp, c.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unread > 9) "9+" else "$unread",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CategoriesSkeleton() {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(5) { Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).shimmer()) }
    }
}
