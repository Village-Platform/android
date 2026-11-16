package com.aghourservices.ui.owner

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aghourservices.R
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.repository.AuthRepository
import com.aghourservices.ui.components.ErrorBanner
import com.aghourservices.ui.components.FormField
import com.aghourservices.ui.components.GhostButton
import com.aghourservices.ui.components.GoldButton
import com.aghourservices.ui.components.ScreenTitle
import com.aghourservices.ui.components.UserAvatar
import com.aghourservices.ui.components.appContainer
import com.aghourservices.ui.components.loadPhotoJpeg
import com.aghourservices.ui.theme.Aghour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileState(
    val name: String = "",
    val email: String = "",
    val nameError: Int? = null,
    val submitting: Boolean = false,
    /** The picture is sent as soon as it is picked or removed, separately from the text fields. */
    val avatarBusy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

/** Issue #169: the signed-in user edits their name, email and picture. */
class EditProfileViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        auth.currentUser.value.let { EditProfileState(name = it?.name.orEmpty(), email = it?.email.orEmpty()) },
    )
    val state: StateFlow<EditProfileState> = _state.asStateFlow()

    fun update(transform: EditProfileState.() -> EditProfileState) =
        _state.update { it.transform().copy(error = null, nameError = null) }

    fun onAvatarUnreadable(message: String) = _state.update { it.copy(error = message) }

    /** [jpeg] null removes the picture. */
    fun setAvatar(jpeg: ByteArray?) {
        if (_state.value.avatarBusy) return
        _state.update { it.copy(avatarBusy = true, error = null) }
        viewModelScope.launch {
            val r = auth.setAvatar(jpeg)
            _state.update { it.copy(avatarBusy = false, error = (r as? ApiResult.Failure)?.message) }
        }
    }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        if (s.name.isBlank()) return _state.update { it.copy(nameError = R.string.error_required) }
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            when (val r = auth.updateProfile(s.name, s.email)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, done = true) }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }
}

@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: EditProfileViewModel = viewModel { EditProfileViewModel(container.auth) }
    val state by vm.state.collectAsStateWithLifecycle()
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.saved)
    val unreadable = stringResource(R.string.error_image_unreadable)
    val c = Aghour.colors

    LaunchedEffect(state.done) {
        if (state.done) {
            Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    // A square-ish profile picture does not need more than 512px.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val jpeg = loadPhotoJpeg(context, uri, maxPx = 512)
            if (jpeg == null) vm.onAvatarUnreadable(unreadable) else vm.setAvatar(jpeg)
        }
    }

    Column(Modifier.fillMaxSize().background(c.page).imePadding()) {
        ScreenTitle(stringResource(R.string.edit_profile), onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(state.name.ifBlank { user?.name.orEmpty() }, user?.avatarUrl, size = 80.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.avatarBusy) {
                        CircularProgressIndicator(color = c.gold)
                    } else {
                        GhostButton(
                            stringResource(R.string.change_photo),
                            { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                        if (user?.avatarUrl != null) {
                            TextButton(onClick = { vm.setAvatar(null) }) {
                                Text(stringResource(R.string.remove_photo), color = c.bad)
                            }
                        }
                    }
                }
            }
            FormField(
                label = stringResource(R.string.name),
                value = state.name,
                onChange = { v -> vm.update { copy(name = v) } },
                error = state.nameError,
            )
            FormField(
                label = stringResource(R.string.email_optional),
                value = state.email,
                onChange = { v -> vm.update { copy(email = v) } },
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onDone = vm::submit,
                ltr = true,
            )
            ErrorBanner(state.error)
            GoldButton(stringResource(R.string.save), vm::submit, loading = state.submitting)
        }
    }
}
