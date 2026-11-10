package com.aghourservices.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.aghourservices.R
import com.aghourservices.data.remote.ApiResult
import com.aghourservices.data.remote.AttachmentDto
import com.aghourservices.ui.theme.Aghour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Mirrors ImageAttachments::MAX on the server; the server still enforces it (422). */
const val MAX_ATTACHMENTS = 5

/** A server path (attachment or avatar url) as an absolute URL on the API host. */
fun apiAssetUrl(path: String?): String? = categoryIconUrl(path)

// ---------------------------------------------------------------- loading a picked photo

private const val PHOTO_MAX_PX = 1600

/**
 * Reads a picked photo and re-encodes it as a JPEG of at most 1600px on its long side, which keeps
 * a phone photo of several MB to a few hundred KB: far under the server's 4MB cap and cheap on a
 * village mobile connection. Decoded with inSampleSize, so a big photo never loads at full size.
 * null = unreadable.
 */
suspend fun loadPhotoJpeg(context: Context, uri: Uri, maxPx: Int = PHOTO_MAX_PX): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val largest = max(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return@runCatching null

        var sample = 1
        while (largest / (sample * 2) >= maxPx) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null

        val scale = maxPx.toFloat() / max(decoded.width, decoded.height)
        val photo = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        ByteArrayOutputStream().use { out ->
            photo.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }.getOrNull()
}

// ---------------------------------------------------------------- editing (forms)

/**
 * The images of a firm or article while its form is open. Nothing is sent until Save: then
 * [applyAttachments] removes [removed] and uploads [added], one request per image.
 */
data class AttachmentsDraft(
    val existing: List<AttachmentDto> = emptyList(),
    val removed: Set<Long> = emptySet(),
    val added: List<ByteArray> = emptyList(),
) {
    val kept: List<AttachmentDto> get() = existing.filter { it.id !in removed }
    val count: Int get() = kept.size + added.size
    val remaining: Int get() = (MAX_ATTACHMENTS - count).coerceAtLeast(0)
    val hasChanges: Boolean get() = removed.isNotEmpty() || added.isNotEmpty()

    fun add(images: List<ByteArray>) = copy(added = added + images.take(remaining))
    fun removeExisting(id: Long) = copy(removed = removed + id)
    fun removeAdded(index: Int) = copy(added = added.filterIndexed { i, _ -> i != index })
}

/** Where [applyAttachments] stopped: [draft] holds what is still to do, [error] the server's message. */
data class AttachmentsResult<T>(val draft: AttachmentsDraft, val last: T?, val error: String?)

/**
 * Sends a draft for a saved record: removals first (they free slots), then each new image.
 * Stops at the first failure and returns what is left, so pressing Save again retries only that.
 * [last] is the record as the server returned it after the last successful call: an added image
 * may have sent it back to review.
 */
suspend fun <T> applyAttachments(
    draft: AttachmentsDraft,
    remove: suspend (Long) -> ApiResult<T>,
    add: suspend (ByteArray) -> ApiResult<T>,
    attachmentsOf: (T) -> List<AttachmentDto>,
): AttachmentsResult<T> {
    var left = draft
    var last: T? = null
    for (id in draft.removed) {
        when (val r = remove(id)) {
            is ApiResult.Success -> {
                last = r.data
                left = left.copy(existing = attachmentsOf(r.data), removed = left.removed - id)
            }
            is ApiResult.Failure -> return AttachmentsResult(left, last, r.message)
        }
    }
    for (image in draft.added) {
        when (val r = add(image)) {
            is ApiResult.Success -> {
                last = r.data
                left = left.copy(existing = attachmentsOf(r.data), added = left.added.drop(1))
            }
            is ApiResult.Failure -> return AttachmentsResult(left, last, r.message)
        }
    }
    return AttachmentsResult(left, last, null)
}

/** Thumbnails of a draft with remove buttons, and an add tile while there is room (system photo picker). */
@Composable
fun AttachmentsEditor(
    draft: AttachmentsDraft,
    onAdd: (List<ByteArray>) -> Unit,
    onUnreadable: () -> Unit,
    onRemoveExisting: (Long) -> Unit,
    onRemoveAdded: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Aghour.colors
    val handle: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val images = uris.map { loadPhotoJpeg(context, it) }
            if (images.any { it == null }) onUnreadable()
            onAdd(images.filterNotNull())
        }
    }
    // PickMultipleVisualMedia needs a limit of at least 2.
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS), handle)
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> handle(listOfNotNull(uri)) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.attachments_label, draft.count, MAX_ATTACHMENTS),
            style = MaterialTheme.typography.labelMedium,
            color = c.muted,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            itemsIndexed(draft.kept, key = { _, a -> "e${a.id}" }) { _, attachment ->
                Thumb(onRemove = { onRemoveExisting(attachment.id) }) {
                    AsyncImage(
                        model = apiAssetUrl(attachment.url),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            itemsIndexed(draft.added) { index, bytes ->
                Thumb(onRemove = { onRemoveAdded(index) }) {
                    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    if (bitmap != null) Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (draft.remaining > 0) {
                item(key = "add") {
                    Box(
                        Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2)
                            .border(1.dp, c.line, RoundedCornerShape(14.dp))
                            .clickable {
                                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                if (draft.remaining >= 2) pickMany.launch(request) else pickOne.launch(request)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.add_images), tint = c.goldInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Aghour.colors.surface2)) {
        content()
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, stringResource(R.string.remove_image), tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------- showing (detail screens)

/** A row of a firm's or article's images; a tap opens them full screen. Nothing when there are none. */
@Composable
fun AttachmentsGallery(attachments: List<AttachmentDto>, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    if (attachments.isEmpty()) return
    var opened by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(attachments, key = { _, a -> a.id }) { index, attachment ->
            AsyncImage(
                model = apiAssetUrl(attachment.url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = height * 1.3f, height = height).clip(RoundedCornerShape(16.dp))
                    .background(Aghour.colors.surface2).clickable { opened = index },
            )
        }
    }
    opened?.let { start -> ImageViewer(attachments, start) { opened = null } }
}

@Composable
private fun ImageViewer(attachments: List<AttachmentDto>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start) { attachments.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = apiAssetUrl(attachments[page].url),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(16.dp).size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = Color.White)
            }
            Text(
                stringResource(R.string.image_counter, pager.currentPage + 1, attachments.size),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
        }
    }
}

/** The user's picture, or the letter avatar when there is none (or it fails to load). */
@Composable
fun UserAvatar(name: String, avatarUrl: String?, size: Dp = 56.dp) {
    val url = apiAssetUrl(avatarUrl)
    if (url == null) {
        LetterAvatar(name, size)
        return
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))) {
        LetterAvatar(name, size)
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
