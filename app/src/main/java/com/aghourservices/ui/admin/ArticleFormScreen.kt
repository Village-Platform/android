package com.aghourservices.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aghourservices.R
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.repository.AdminRepository
import com.aghourservices.data.repository.ArticleInput
import com.aghourservices.data.repository.CatalogRepository
import com.aghourservices.ui.components.AttachmentsDraft
import com.aghourservices.ui.components.AttachmentsEditor
import com.aghourservices.ui.components.FormField
import com.aghourservices.ui.components.applyAttachments
import com.aghourservices.ui.components.MutedText
import com.aghourservices.ui.components.Segmented
import com.aghourservices.ui.components.SwitchRow
import com.aghourservices.ui.components.appContainer
import com.aghourservices.ui.components.kindLabel
import com.aghourservices.ui.theme.Aghour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/** Article kinds the server accepts (Article#kind). */
private val ArticleKinds = listOf("news", "announcement", "article")

data class ArticleFormUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val title: String = "",
    val body: String = "",
    val kind: String = "news",
    val published: Boolean = true,
    /** Kept when an already published article is edited, so its date does not move. */
    val publishedAt: String? = null,
    /** Images: sent after the article itself is saved (one request each). */
    val attachments: AttachmentsDraft = AttachmentsDraft(),
    val imageError: Int? = null,
    val titleError: Int? = null,
    val bodyError: Int? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
    val sentForReview: Boolean = false,
    val deleted: Boolean = false,
)

class ArticleFormViewModel(
    private val catalog: CatalogRepository,
    private val admin: AdminRepository,
    private val articleId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(ArticleFormUiState(loading = articleId != null))
    val state: StateFlow<ArticleFormUiState> = _state.asStateFlow()

    /** The article on the server: set after a create, so a retry after a failed upload edits it instead. */
    private var savedArticleId: Long? = articleId

    init {
        if (articleId != null) load()
    }

    fun load() {
        val id = articleId ?: return
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = catalog.article(id)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        title = r.data.title,
                        body = r.data.body,
                        kind = r.data.kind,
                        published = r.data.publishedAt != null,
                        publishedAt = r.data.publishedAt,
                        attachments = AttachmentsDraft(existing = r.data.attachments),
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, loadError = r.message) }
            }
        }
    }

    fun update(transform: ArticleFormUiState.() -> ArticleFormUiState) =
        _state.update { it.transform().copy(error = null, titleError = null, bodyError = null, imageError = null) }

    fun onImagesUnreadable() = _state.update { it.copy(imageError = R.string.error_image_unreadable) }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        val titleError = if (s.title.isBlank()) R.string.error_required else null
        val bodyError = if (s.body.isBlank()) R.string.error_required else null
        if (titleError != null || bodyError != null) {
            _state.update { it.copy(titleError = titleError, bodyError = bodyError) }
            return
        }
        // Published: keep the original date, or publish now. Draft: no date.
        val publishedAt = if (s.published) s.publishedAt ?: OffsetDateTime.now().toString() else null
        val input = ArticleInput(s.title, s.body, s.kind, publishedAt)
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val id = savedArticleId
            val article = when (val r = if (id == null) admin.createArticle(input) else admin.updateArticle(id, input)) {
                is ApiResult.Success -> r.data
                is ApiResult.Failure -> return@launch _state.update { it.copy(submitting = false, error = r.message) }
            }
            savedArticleId = article.id
            // The article is saved; now its images. An added image may send it back to review.
            val sync = applyAttachments(
                _state.value.attachments,
                remove = { admin.deleteArticleAttachment(article.id, it) },
                add = { admin.addArticleAttachment(article.id, it) },
                attachmentsOf = { it.attachments },
            )
            val latest = sync.last ?: article
            _state.update {
                if (sync.error != null) {
                    it.copy(submitting = false, attachments = sync.draft, error = sync.error)
                } else {
                    it.copy(
                        submitting = false,
                        attachments = sync.draft,
                        savedId = latest.id,
                        sentForReview = latest.reviewStatus == "pending" || latest.publishedAt == null,
                    )
                }
            }
        }
    }

    fun delete() {
        val id = articleId ?: return
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = admin.deleteArticle(id)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, deleted = true) }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }
}

/** Create ([articleId] null) or edit a news item / announcement / article. */
@Composable
fun ArticleFormScreen(articleId: Long?, onBack: () -> Unit, onSaved: (Long) -> Unit, onDeleted: () -> Unit) {
    val container = appContainer()
    val vm: ArticleFormViewModel = viewModel { ArticleFormViewModel(container.catalog, container.admin, articleId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.saved)
    val deletedMessage = stringResource(R.string.deleted)
    var showReviewDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.savedId) {
        val id = state.savedId ?: return@LaunchedEffect
        if (state.sentForReview) {
            showReviewDialog = true
        } else {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            onSaved(id)
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            Toast.makeText(context, deletedMessage, Toast.LENGTH_SHORT).show()
            onDeleted()
        }
    }

    AdminFormScaffold(
        title = stringResource(if (articleId == null) R.string.add_article else R.string.edit_article),
        onBack = onBack,
        loading = state.loading,
        loadError = state.loadError,
        onRetry = vm::load,
        error = state.error,
        submitting = state.submitting,
        onSave = vm::submit,
        delete = articleId?.let {
            DeleteAction(
                title = stringResource(R.string.delete_article_title),
                body = stringResource(R.string.delete_article_body, state.title),
                onDelete = vm::delete,
            )
        },
    ) {
        FormField(
            label = stringResource(R.string.article_title),
            value = state.title,
            onChange = { v -> vm.update { copy(title = v) } },
            error = state.titleError,
        )
        FieldLabel(stringResource(R.string.article_kind))
        Row {
            Segmented(
                options = ArticleKinds.map { it to kindLabel(it) },
                selected = state.kind,
                onSelect = { k -> vm.update { copy(kind = k) } },
            )
        }
        if (articleId == null) {
            MutedText(stringResource(R.string.article_review_note))
        }
        SwitchRow(stringResource(R.string.published), state.published, { v -> vm.update { copy(published = v) } })
        FormField(
            label = stringResource(R.string.article_body),
            value = state.body,
            onChange = { v -> vm.update { copy(body = v) } },
            error = state.bodyError,
            imeAction = ImeAction.Default,
            multiline = true,
        )
        AttachmentsEditor(
            draft = state.attachments,
            onAdd = { images -> vm.update { copy(attachments = attachments.add(images)) } },
            onUnreadable = vm::onImagesUnreadable,
            onRemoveExisting = { id -> vm.update { copy(attachments = attachments.removeExisting(id)) } },
            onRemoveAdded = { i -> vm.update { copy(attachments = attachments.removeAdded(i)) } },
        )
        FieldError(state.imageError)
    }

    if (showReviewDialog && state.savedId != null) {
        val savedId = state.savedId!!
        val c = Aghour.colors
        AlertDialog(
            onDismissRequest = {
                showReviewDialog = false
                onSaved(savedId)
            },
            title = { Text(stringResource(R.string.submission_success_title), fontWeight = FontWeight.Bold, color = c.ink) },
            text = { Text(stringResource(R.string.article_sent_for_review_message), color = c.muted) },
            confirmButton = {
                TextButton(onClick = {
                    showReviewDialog = false
                    onSaved(savedId)
                }) {
                    Text(stringResource(R.string.ok), color = c.goldInk)
                }
            },
        )
    }
}
