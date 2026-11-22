package com.sxsbsxs.filesniffing.presentation.sniffing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sxsbsxs.filesniffing.ResourceKind
import com.sxsbsxs.filesniffing.SniffedResource
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsSheet(
    resources: List<SniffedResource>,
    onDismiss: () -> Unit,
    onStartDownload: (SniffedResource) -> Unit
) {
    val videoChoices = remember(resources) {
        resources.filter {
            it.kind == ResourceKind.VIDEO &&
                !it.videoFormatId.isNullOrBlank() &&
                !it.formatSelector.isNullOrBlank()
        }
    }
    val audioChoices = remember(resources) {
        resources.filter {
            it.kind == ResourceKind.AUDIO &&
                (!it.audioFormatId.isNullOrBlank() || !it.formatSelector.isNullOrBlank())
        }
    }
    val directChoice = remember(resources) {
        resources.firstOrNull { it !in videoChoices && it !in audioChoices }
    }

    var mode by remember(resources) {
        mutableStateOf(
            when {
                videoChoices.isNotEmpty() -> "video"
                audioChoices.isNotEmpty() -> "audio"
                else -> "file"
            }
        )
    }
    var selectedVideoKey by remember(resources) {
        mutableStateOf(videoChoices.firstOrNull()?.downloadKey.orEmpty())
    }
    var selectedAudioId by remember(resources) {
        mutableStateOf(videoChoices.firstOrNull()?.audioFormatId.orEmpty())
    }

    val selectedVideo = videoChoices.firstOrNull { it.downloadKey == selectedVideoKey }
        ?: videoChoices.firstOrNull()
    val selectedAudio = audioChoices.firstOrNull {
        (it.audioFormatId ?: it.formatSelector) == selectedAudioId
    }

    LaunchedEffect(selectedVideoKey) {
        selectedVideo?.let { video ->
            selectedAudioId = if (video.hasEmbeddedAudio) {
                ""
            } else {
                video.audioFormatId
                    ?: audioChoices.firstOrNull()?.audioFormatId
                    ?: audioChoices.firstOrNull()?.formatSelector.orEmpty()
            }
        }
    }

    LaunchedEffect(mode) {
        if (mode == "audio" && selectedAudio == null) {
            selectedAudioId = audioChoices.firstOrNull()?.audioFormatId
                ?: audioChoices.firstOrNull()?.formatSelector.orEmpty()
        }
    }

    val selectedResource = when (mode) {
        "video" -> selectedVideo?.let { video ->
            val audio = if (video.hasEmbeddedAudio) null else selectedAudio
            DownloadFormatComposer.video(video, audio)
        }
        "audio" -> DownloadFormatComposer.audio(selectedAudio)
        else -> directChoice
    }
    val isReady = selectedResource != null &&
        (mode != "video" || selectedResource.formatSelector?.isNotBlank() == true)

    val uploader = resources.firstNotNullOfOrNull { it.uploader }?.takeIf { it.isNotBlank() }
    val duration = resources.firstNotNullOfOrNull { it.durationSeconds }?.let(::formatMediaDuration)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        // A single LazyColumn participates in the bottom sheet's nested-scroll system.
        // The previous Column + verticalScroll composed every codec row and could fight
        // the sheet's own drag/overscroll at the lower edge on long Niconico format lists.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 22.dp,
                top = 8.dp,
                end = 22.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "options-title") {
                Text(
                    "下载配置",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item(key = "options-media-title") {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        resources.firstOrNull()?.pageTitle?.ifBlank { "媒体文件" } ?: "媒体文件",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (uploader != null || duration != null) {
                        Text(
                            listOfNotNull(uploader, duration).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (videoChoices.isNotEmpty() && audioChoices.isNotEmpty()) {
                item(key = "options-mode") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == "video",
                            onClick = { mode = "video" },
                            label = { Text("视频") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.VideoFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = mode == "audio",
                            onClick = { mode = "audio" },
                            label = { Text("仅音频") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.AudioFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        )
                    }
                }
            }

            when (mode) {
                "video" -> {
                    item(key = "video-quality-heading") {
                        Text(
                            "视频画质",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(
                        items = videoChoices,
                        key = { choice -> "video:${choice.downloadKey}" }
                    ) { choice ->
                        FormatChoiceRow(
                            selected = choice.downloadKey == selectedVideo?.downloadKey,
                            title = choice.qualityLabel ?: "视频格式",
                            subtitle = buildFormatDetails(choice),
                            onClick = { selectedVideoKey = choice.downloadKey }
                        )
                    }

                    selectedVideo?.let { video ->
                        item(key = "audio-heading") {
                            Text(
                                "音轨",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (video.hasEmbeddedAudio) {
                            item(key = "embedded-audio") {
                                FormatChoiceRow(
                                    selected = true,
                                    title = "使用视频自带音轨",
                                    subtitle = video.codecLabel?.let { "内嵌 · $it" }
                                        ?: "此视频格式已包含音频",
                                    onClick = { selectedAudioId = "" }
                                )
                            }
                        } else {
                            items(
                                items = audioChoices,
                                key = { choice ->
                                    "audio:${choice.audioFormatId ?: choice.formatSelector.orEmpty()}"
                                }
                            ) { choice ->
                                val audioId = choice.audioFormatId ?: choice.formatSelector.orEmpty()
                                FormatChoiceRow(
                                    selected = selectedAudioId == audioId,
                                    title = choice.qualityLabel ?: "音频格式",
                                    subtitle = buildFormatDetails(choice),
                                    onClick = { selectedAudioId = audioId }
                                )
                            }
                        }
                        if (!video.hasEmbeddedAudio && selectedAudio == null) {
                            item(key = "audio-warning") {
                                Text(
                                    "此画质没有内嵌音轨，请选择一个可用音轨。",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                "audio" -> {
                    item(key = "audio-quality-heading") {
                        Text(
                            "音频质量",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(
                        items = audioChoices,
                        key = { choice ->
                            "audio-only:${choice.audioFormatId ?: choice.formatSelector.orEmpty()}"
                        }
                    ) { choice ->
                        val audioId = choice.audioFormatId ?: choice.formatSelector.orEmpty()
                        FormatChoiceRow(
                            selected = selectedAudioId == audioId,
                            title = choice.qualityLabel ?: "音频格式",
                            subtitle = buildFormatDetails(choice),
                            onClick = { selectedAudioId = audioId }
                        )
                    }
                }

                else -> {
                    item(key = "direct-file") {
                        Text(
                            directChoice?.fileName ?: "原始文件",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item(key = "options-divider") {
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item(key = "options-summary") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "将下载",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                when (mode) {
                                    "video" -> "视频 · " + (selectedResource?.qualityLabel ?: "请选择画质")
                                    "audio" -> "仅音频 · " + (selectedResource?.qualityLabel ?: "请选择音质")
                                    else -> directChoice?.kind?.label ?: "文件"
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                selectedResource?.let {
                                    buildString {
                                        append(it.fileName)
                                        it.fileSizeBytes?.takeIf { size -> size > 0L }?.let { size ->
                                            append(" · ").append(formatMediaBytes(size))
                                        }
                                    }
                                } ?: "选择有效的视频画质与音轨",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item(key = "options-start-download") {
                Button(
                    onClick = {
                        selectedResource?.let(onStartDownload)
                        onDismiss()
                    },
                    enabled = isReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("加入下载队列")
                }
            }
        }
    }
}

@Composable
private fun FormatChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildFormatDetails(resource: SniffedResource): String = buildList {
    resource.fileSizeBytes?.takeIf { it > 0L }?.let { add(formatMediaBytes(it)) }
    resource.frameRate?.takeIf { it > 0.0 }?.let { add(it.toInt().toString() + " fps") }
    resource.codecLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
    resource.audioLanguage?.takeIf { it.isNotBlank() }?.let { add(it) }
    resource.uploader?.takeIf { it.isNotBlank() }?.let { add(it) }
}.joinToString(" · ").ifBlank { resource.fileName }

private fun formatMediaBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", value / (1024.0 * 1024.0))
    value >= 1024L -> String.format(Locale.ROOT, "%.1f KB", value / 1024.0)
    else -> value.toString() + " B"
}

private fun formatMediaDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val remaining = total % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remaining)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
