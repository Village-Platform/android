package com.aghourservices.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aghourservices.BuildConfig
import com.aghourservices.R
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.remote.FirmDto
import com.aghourservices.data.remote.UserDto
import com.aghourservices.data.remote.canManageFirms
import com.aghourservices.data.remote.isSuperAdmin
import com.aghourservices.data.repository.AuthRepository
import com.aghourservices.data.repository.OwnerRepository
import com.aghourservices.ui.components.Badge
import com.aghourservices.ui.components.BadgeTone
import com.aghourservices.ui.components.BrandLogo
import com.aghourservices.ui.components.CheckLine
import com.aghourservices.ui.components.Chevron
import com.aghourservices.ui.components.GhostButton
import com.aghourservices.ui.components.GoldButton
import com.aghourservices.ui.components.UserAvatar
import com.aghourservices.ui.components.MutedText
import com.aghourservices.ui.components.ScreenTitle
import com.aghourservices.ui.components.StatusBarIcons
import com.aghourservices.ui.components.SurfaceCard
import com.aghourservices.ui.components.appContainer
import com.aghourservices.ui.components.relativeTime
import com.aghourservices.ui.components.roleLabel
import com.aghourservices.ui.theme.Aghour
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val firms: List<FirmDto> = emptyList(),
    val current: com.aghourservices.data.remote.SubscriptionDto? = null,
    val pending: com.aghourservices.data.remote.SubscriptionDto? = null,
)

class ProfileViewModel(private val auth: AuthRepository, private val owner: OwnerRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Called whenever the profile is shown while signed in (role/subscription may have changed). */
    fun refresh() {
        viewModelScope.launch {
            auth.refreshMe()
            val subs = async { owner.subscriptions(page = 1, perPage = 50) }
            val firms = async { owner.myFirms(page = 1, perPage = 50) }
            val list = (subs.await() as? ApiResult.Success)?.data?.data.orEmpty()
            _state.value = ProfileUiState(
                firms = (firms.await() as? ApiResult.Success)?.data?.data.orEmpty(),
                current = list.firstOrNull { it.state == "current" },
                pending = list.firstOrNull { it.state == "pending" },
            )
        }
    }
}

@Composable
fun AccountScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    /** Both null while plans are switched off: the subscription card and these rows are hidden. */
    onPlans: (() -> Unit)?,
    onPayments: (() -> Unit)?,
    onNotifications: () -> Unit,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onMyFirms: () -> Unit,
    onAddFirm: () -> Unit,
    onAddArticle: () -> Unit,
    onSettings: () -> Unit,
    onDeleteAccount: () -> Unit,
    /** Super admin only (null for everyone else): the admin menu. */
    admin: AdminMenu? = null,
    /** Firm admins see "My firms" even before adding one, so they can add their first firm. */
    alwaysShowMyFirms: Boolean = false,
) {
    val container = appContainer()
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val vm: ProfileViewModel = viewModel { ProfileViewModel(container.auth, container.owner) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val c = Aghour.colors
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    StatusBarIcons()

    LaunchedEffect(user?.id) { if (user != null) vm.refresh() }

    val current = user
    if (current == null) {
        GuestProfile(onLogin, onRegister, onSettings, onNotifications = onNotifications, onAbout = { showAbout = true })
    } else {
        Column(Modifier.fillMaxSize().background(c.page).verticalScroll(rememberScrollState())) {
            ProfileHeader(current)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (onPlans != null) SubscriptionCard(state, onPlans)
                if (admin != null) AdminMenuCard(admin)
                SurfaceCard(padding = 0.dp) {
                    MenuRow(Icons.Outlined.Storefront, stringResource(R.string.my_firms), "${state.firms.size}", onMyFirms)
                    MenuDivider()
                    MenuRow(Icons.Outlined.AddBusiness, stringResource(R.string.add_firm), null, onAddFirm)
                    MenuDivider()
                    MenuRow(Icons.Outlined.PostAdd, stringResource(R.string.add_article), null, onAddArticle)
                    MenuDivider()
                    if (onPlans != null) {
                        MenuRow(Icons.Outlined.CreditCard, stringResource(R.string.subscription_and_plans), null, onPlans)
                        MenuDivider()
                    }
                    if (onPayments != null) {
                        MenuRow(Icons.Outlined.Payments, stringResource(R.string.payments_history), null, onPayments)
                        MenuDivider()
                    }
                    MenuRow(Icons.Outlined.Notifications, stringResource(R.string.notifications), null, onNotifications)
                    MenuDivider()
                    MenuRow(Icons.Outlined.Palette, stringResource(R.string.appearance_menu), null, onSettings)
                    MenuDivider()
                    MenuRow(Icons.Outlined.ManageAccounts, stringResource(R.string.edit_profile), null, onEditProfile)
                    MenuDivider()
                    MenuRow(Icons.Outlined.Lock, stringResource(R.string.change_password), null, onChangePassword)
                    MenuDivider()
                    MenuRow(Icons.Outlined.Info, stringResource(R.string.about_app), null) { showAbout = true }
                    MenuDivider()
                    MenuRow(Icons.Outlined.DeleteForever, stringResource(R.string.delete_account), null, onDeleteAccount)
                }
                Button(
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.bad.copy(alpha = 0.10f), contentColor = c.bad),
                ) { Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_confirm_title), fontWeight = FontWeight.Bold, color = c.ink) },
            text = { Text(stringResource(R.string.logout_confirm_body), color = c.muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch { container.auth.logout() }
                }) { Text(stringResource(R.string.logout), color = c.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel), color = c.ink) } },
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { BrandLogo(Modifier.size(110.dp)) },
            text = { Text(stringResource(R.string.about_app_body, BuildConfig.VERSION_NAME), textAlign = TextAlign.Center) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.ok), color = c.goldInk) } },
        )
    }
}

@Composable
private fun ProfileHeader(user: UserDto) {
    val c = Aghour.colors
    Column {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(user.name, user.avatarUrl, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                Text("\u2066${user.email ?: user.phone}\u2069", color = c.muted, style = MaterialTheme.typography.bodySmall)
                roleLabel(user.role)?.let { Badge(it, BadgeTone.Gold) }
            }
        }
        HorizontalDivider(color = c.line)
    }
}

/** Current plan with days left and a progress bar, a pending request, or a call to subscribe. */
@Composable
private fun SubscriptionCard(state: ProfileUiState, onPlans: () -> Unit) {
    val c = Aghour.colors
    SurfaceCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.firms.firstOrNull()?.name ?: stringResource(R.string.subscription_and_plans),
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            when {
                state.current != null -> Badge("موثّق", icon = Icons.Outlined.Verified)
                state.pending != null -> Badge("قيد المراجعة", BadgeTone.Gold)
            }
        }
        Spacer(Modifier.height(10.dp))
        val current = state.current
        if (current != null) {
            val total = current.plan.durationDays.coerceAtLeast(1)
            val left = (current.daysLeft ?: 0).coerceIn(0, total)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.gold.copy(alpha = 0.10f)).padding(12.dp)) {
                Row {
                    Text("باقة ${current.plan.name}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.days_left, left), style = MaterialTheme.typography.labelMedium, color = c.ink)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { left.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = c.gold,
                    trackColor = c.line,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            }
        } else if (state.pending == null) {
            MutedText(stringResource(R.string.no_subscription))
            Spacer(Modifier.height(10.dp))
            GoldButton(stringResource(R.string.subscribe_now), onPlans)
        } else {
            MutedText(stringResource(R.string.payment_sent_body))
        }
    }
}

class AdminMenu(
    val onCategories: () -> Unit,
    val onPlans: () -> Unit,
)

@Composable
private fun AdminMenuCard(admin: AdminMenu) {
    val c = Aghour.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.admin_section), style = MaterialTheme.typography.titleSmall, color = c.ink)
        SurfaceCard(padding = 0.dp) {
            MenuRow(Icons.Outlined.Category, stringResource(R.string.manage_categories), null, admin.onCategories)
            MenuDivider()
            MenuRow(Icons.Outlined.Sell, stringResource(R.string.manage_plans), null, admin.onPlans)
        }
    }
}

@Composable
private fun GuestProfile(onLogin: () -> Unit, onRegister: () -> Unit, onSettings: () -> Unit, onNotifications: () -> Unit, onAbout: () -> Unit) {
    val c = Aghour.colors
    Column(
        Modifier.fillMaxSize().background(c.page).verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandLogo(Modifier.height(112.dp).align(Alignment.CenterHorizontally))
        Text(stringResource(R.string.guest_profile_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = c.ink)
        MutedText(stringResource(R.string.guest_profile_body))
        listOf(R.string.benefit_firms, R.string.benefit_notifications, R.string.benefit_free).forEach { res ->
            CheckLine(stringResource(res), color = c.ink)
        }
        GoldButton(stringResource(R.string.login), onLogin)
        GhostButton(stringResource(R.string.register), onRegister, height = 50.dp)
        SurfaceCard(padding = 0.dp) {
            MenuRow(Icons.Outlined.Notifications, stringResource(R.string.notifications), null, onNotifications)
            MenuDivider()
            MenuRow(Icons.Outlined.Palette, stringResource(R.string.appearance_menu), null, onSettings)
            MenuDivider()
            MenuRow(Icons.Outlined.Info, stringResource(R.string.about_app), null, onAbout)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, trailing: String?, onClick: () -> Unit) {
    val c = Aghour.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.goldInk, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Spacer(Modifier.width(8.dp))
        }
        Chevron()
    }
}

@Composable
private fun MenuDivider() {
    val c = Aghour.colors
    HorizontalDivider(color = c.line, modifier = Modifier.padding(start = 48.dp))
}
