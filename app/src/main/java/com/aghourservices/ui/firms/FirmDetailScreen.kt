package com.aghourservices.ui.firms

import com.aghourservices.data.analytics.AnalyticsEvent
import com.aghourservices.data.analytics.AnalyticsService
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aghourservices.R
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.remote.FirmDto
import com.aghourservices.data.repository.AdminRepository
import com.aghourservices.data.repository.CatalogRepository
import com.aghourservices.ui.components.Badge
import com.aghourservices.ui.components.BadgeTone
import com.aghourservices.ui.components.ConfirmDeleteDialog
import com.aghourservices.ui.components.DangerButton
import com.aghourservices.ui.components.ErrorBanner
import com.aghourservices.ui.components.GhostButton
import com.aghourservices.ui.components.MessageBox
import com.aghourservices.ui.components.OverlayIconButton
import com.aghourservices.ui.components.ScreenTitle
import com.aghourservices.ui.components.AttachmentsGallery
import com.aghourservices.ui.components.SheetLayout
import com.aghourservices.ui.components.SkeletonRows
import com.aghourservices.ui.components.SurfaceCard
import com.aghourservices.ui.components.appContainer
import com.aghourservices.ui.components.asHeading
import com.aghourservices.ui.components.firmStatusLabel
import com.aghourservices.ui.components.openDialer
import com.aghourservices.ui.components.openMap
import com.aghourservices.ui.components.openWhatsApp
import com.aghourservices.ui.components.shareText
import com.aghourservices.ui.theme.Aghour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FirmDetailUiState(
    val loading: Boolean = true,
    val firm: FirmDto? = null,
    val error: String? = null,
    /** Super admin actions: a status change or delete is running / failed / finished. */
    val busy: Boolean = false,
    val actionError: String? = null,
    val deleted: Boolean = false,
)

class FirmDetailViewModel(
    private val catalog: CatalogRepository,
    private val admin: AdminRepository,
    private val analytics: AnalyticsService,
    private val firmId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(FirmDetailUiState())
    val state: StateFlow<FirmDetailUiState> = _state.asStateFlow()

    init {
        load()
        // Edited elsewhere (the form) -> show the new data. Not while deleting: the firm is gone.
        viewModelScope.launch {
            admin.changes.drop(1).collect { if (!_state.value.busy && !_state.value.deleted) load() }
        }
    }

    /** view_firm once per visit, not again when an edit reloads the page. */
    private var viewLogged = false

    fun load() {
        _state.value = FirmDetailUiState(loading = true)
        viewModelScope.launch {
            _state.value = when (val result = catalog.firm(firmId)) {
                is ApiResult.Success -> {
                    if (!viewLogged) analytics.log(AnalyticsEvent.ViewFirm(result.data))
                    viewLogged = true
                    FirmDetailUiState(loading = false, firm = result.data)
                }
                is ApiResult.Failure -> FirmDetailUiState(loading = false, error = result.message)
            }
        }
    }

    fun setStatus(status: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            when (val r = admin.setFirmStatus(firmId, status)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, firm = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, actionError = r.message) }
            }
        }
    }

    fun delete() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            when (val r = admin.deleteFirm(firmId)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, deleted = true) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, actionError = r.message) }
            }
        }
    }
}

/**
 * Which firm controls to show. The server sends them in `can` (from its FirmPolicy); an older API
 * without it falls back to the same rule worked out here: the owner or the super admin may edit and
 * delete, only the super admin approves or suspends. A UI hint only: the server still decides.
 */
data class FirmAccess(val userId: Long?, val isSuperAdmin: Boolean) {
    private fun ownsOrAdmin(firm: FirmDto) = isSuperAdmin || (userId != null && firm.ownerId == userId)

    fun canEdit(firm: FirmDto): Boolean = firm.can?.update ?: ownsOrAdmin(firm)
    fun canDelete(firm: FirmDto): Boolean = firm.can?.destroy ?: ownsOrAdmin(firm)
    fun canModerate(firm: FirmDto): Boolean = firm.can?.moderate ?: isSuperAdmin

    companion object {
        val Guest = FirmAccess(userId = null, isSuperAdmin = false)
    }
}

/** [access] (built in AppNavigation) decides which of the edit / approve / suspend / delete controls show. */
@Composable
fun FirmDetailScreen(
    firmId: Long,
    onBack: () -> Unit,
    access: FirmAccess = FirmAccess.Guest,
    onEdit: () -> Unit = {},
    onDeleted: () -> Unit = onBack,
) {
    val container = appContainer()
    val vm: FirmDetailViewModel = viewModel { FirmDetailViewModel(container.catalog, container.admin, container.analytics, firmId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val firm = state.firm
    val context = LocalContext.current
    val deletedMessage = stringResource(R.string.firm_deleted)

    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            Toast.makeText(context, deletedMessage, Toast.LENGTH_SHORT).show()
            onDeleted()
        }
    }

    if (firm != null) {
        FirmDetailContent(firm, onBack) {
            if (access.canEdit(firm) || access.canDelete(firm)) {
                ManageFirmControls(
                    firm = firm,
                    busy = state.busy,
                    error = state.actionError,
                    onEdit = if (access.canEdit(firm)) onEdit else null,
                    // Owners cannot approve their own firm.
                    onSetStatus = if (access.canModerate(firm)) vm::setStatus else null,
                    onDelete = if (access.canDelete(firm)) vm::delete else null,
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Aghour.colors.page)) {
            ScreenTitle(title = "", onBack = onBack)
            if (state.loading) SkeletonRows(3, Modifier.padding(16.dp)) else MessageBox(state.error ?: "", onRetry = vm::load)
        }
    }
}

@Composable
private fun FirmDetailContent(
    firm: FirmDto,
    onBack: () -> Unit,
    adminControls: @Composable ColumnScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val analytics = appContainer().analytics
    val c = Aghour.colors
    val isDark = c.isDark

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
                    OverlayIconButton({
                        analytics.log(AnalyticsEvent.Share("firm", firm.id))
                        context.shareText("${firm.name}\n\n${firm.address.orEmpty()}\n${firm.phone.orEmpty()}")
                    }) {
                        Icon(Icons.Filled.Share, stringResource(R.string.share), tint = Color.White)
                    }
                }
            }
        },
    ) {
        // Only owners and the super admin can open a pending / suspended firm; say so clearly.
        if (firm.status != "active") {
            Badge(firmStatusLabel(firm.status), if (firm.status == "suspended") BadgeTone.Red else BadgeTone.Gold)
        }

        Text(firm.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = c.ink, modifier = Modifier.asHeading())

        val phone = firm.phone?.takeIf { it.isNotBlank() }
        val whatsappNumber = firm.whatsapp?.takeIf { it.isNotBlank() } ?: phone
        val hasCoords = firm.latitude != null && firm.longitude != null

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val callColor = if (isDark) CallDark else CallLight
            val mapColor = if (isDark) MapDark else MapLight

            ActionTile(
                icon = rememberVectorPainter(Icons.Filled.Call),
                label = stringResource(R.string.call),
                color = callColor,
                enabled = phone != null,
                modifier = Modifier.weight(1f),
            ) {
                phone?.let {
                    analytics.log(AnalyticsEvent.ContactFirm(firm, AnalyticsEvent.ContactChannel.Call))
                    context.openDialer(it)
                }
            }
            ActionTile(
                icon = painterResource(R.drawable.ic_whatsapp),
                label = stringResource(R.string.whatsapp),
                color = WhatsAppGreen,
                enabled = whatsappNumber != null,
                modifier = Modifier.weight(1f),
            ) {
                whatsappNumber?.let {
                    analytics.log(AnalyticsEvent.ContactFirm(firm, AnalyticsEvent.ContactChannel.WhatsApp))
                    context.openWhatsApp(it)
                }
            }
            ActionTile(
                icon = rememberVectorPainter(Icons.Filled.LocationOn),
                label = stringResource(R.string.open_map),
                color = mapColor,
                enabled = hasCoords,
                modifier = Modifier.weight(1f),
            ) {
                if (firm.latitude != null && firm.longitude != null) {
                    analytics.log(AnalyticsEvent.ContactFirm(firm, AnalyticsEvent.ContactChannel.Map))
                    context.openMap(firm.latitude, firm.longitude, firm.name)
                }
            }
        }

        HorizontalDivider(color = c.line, thickness = 1.dp)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoLine(Icons.Outlined.Place, firm.address)
            InfoLine(Icons.Outlined.Schedule, firm.workingHours)
            InfoLine(Icons.Outlined.PhoneAndroid, firm.phone)
        }

        firm.description?.takeIf { it.isNotBlank() }?.let { desc ->
            HorizontalDivider(color = c.line, thickness = 1.dp)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = c.ink)
        }

        AttachmentsGallery(firm.attachments)

        HorizontalDivider(color = c.line, thickness = 1.dp)

        adminControls()
    }
}

/** Each control shows only when its callback is set: edit, approve / suspend, delete (after a confirmation). */
@Composable
private fun ManageFirmControls(
    firm: FirmDto,
    busy: Boolean,
    error: String?,
    onEdit: (() -> Unit)?,
    onSetStatus: ((String) -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val c = Aghour.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.manage_firm), style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.asHeading())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onEdit != null) GhostButton(stringResource(R.string.edit), onEdit, Modifier.weight(1f))
            if (onSetStatus != null) {
                if (firm.status == "active") {
                    GhostButton(stringResource(R.string.suspend), { if (!busy) onSetStatus("suspended") }, Modifier.weight(1f))
                } else {
                    GhostButton(stringResource(R.string.approve), { if (!busy) onSetStatus("active") }, Modifier.weight(1f))
                }
            }
            if (onDelete != null) DangerButton(stringResource(R.string.delete), { if (!busy) confirmDelete = true }, Modifier.weight(1f))
        }
        ErrorBanner(error)
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_firm_title),
            body = stringResource(R.string.delete_firm_body, firm.name),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// Action colours (light / dark shade): green call, red map pin, WhatsApp brand green.
private val CallLight = Color(0xFF1A7F37)
private val CallDark = Color(0xFF3FB950)
private val MapLight = Color(0xFFD1242F)
private val MapDark = Color(0xFFFF7B72)
private val WhatsAppGreen = Color(0xFF25D366)

/** Call / location / WhatsApp tile: the icon in its own colour on a light tint of it; grey when unavailable. */
@Composable
private fun ActionTile(icon: Painter, label: String, color: Color, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Aghour.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (enabled) color.copy(alpha = if (c.isDark) 0.16f else 0.10f) else c.surface)
            .border(1.dp, if (enabled) color.copy(alpha = 0.35f) else c.line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) color else c.line, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (enabled) c.ink else c.muted)
    }
}

@Composable
private fun InfoLine(icon: ImageVector, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Aghour.colors.goldInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(value?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodyMedium, color = Aghour.colors.ink)
    }
}
