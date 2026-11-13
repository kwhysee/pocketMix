package com.kwhysee.pocketmix

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun WorkspaceScreen(
    videos: List<Video>,
    pockets: List<PocketState>,
    playbackStates: Map<String, PlaybackState>,
    onPlaybackStateChange: (PlaybackState) -> Unit,
    onBackToLibrary: () -> Unit,
    onPocketChange: (PocketState) -> Unit,
    onArrangePockets: () -> Unit,
    onStartFloatingWindows: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openPockets = pockets
        .filter { it.status == PocketStatus.OPEN }
        .sortedBy { it.order }
    val minimizedPockets = pockets
        .filter { it.status == PocketStatus.MINIMIZED }

    var audioPocketId by remember {
        mutableStateOf<String?>(null)
    }
    var pauseAllRequest by remember {
        mutableIntStateOf(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "작업 공간",
            style = MaterialTheme.typography.headlineMedium,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("열린 Pocket ${openPockets.size}개")
            Button(onClick = onBackToLibrary) {
                Text("영상 추가")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { pauseAllRequest += 1 },
                enabled = openPockets.isNotEmpty(),
            ) {
                Text("모두 일시정지")
            }
            OutlinedButton(
                onClick = onArrangePockets,
                enabled = openPockets.isNotEmpty(),
            ) {
                Text("창 정렬")
            }
        }

        Button(
            onClick = onStartFloatingWindows,
            enabled = openPockets.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("떠 있는 창으로 보기")
        }

        if (minimizedPockets.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                minimizedPockets.forEach { pocket ->
                    val title = videos.findUsableVideo(pocket.videoId)?.title
                        ?: pocket.videoId
                    OutlinedButton(
                        onClick = {
                            val nextOrder =
                                (pockets.maxOfOrNull { it.order } ?: -1) + 1
                            onPocketChange(
                                pocket.copy(
                                    status = PocketStatus.OPEN,
                                    order = nextOrder,
                                )
                            )
                        },
                    ) {
                        Text("복원: $title")
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val boardWidthPx = constraints.maxWidth.toFloat()
            val boardHeightPx = constraints.maxHeight.toFloat()

            if (openPockets.isEmpty()) {
                Text(
                    text = "열린 Pocket이 없습니다. '영상 추가'로 영상을 선택하세요.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            openPockets.forEach { pocket ->
                key(pocket.videoId) {
                    PocketWindow(
                        pocket = pocket,
                        video = videos.findUsableVideo(pocket.videoId),
                        playbackState = playbackStates[pocket.videoId]
                            ?: PlaybackState(videoId = pocket.videoId),
                        pauseAllRequest = pauseAllRequest,
                        boardWidthPx = boardWidthPx,
                        boardHeightPx = boardHeightPx,
                        onPlaybackStateChange = onPlaybackStateChange,
                        onPocketChange = onPocketChange,
                        onAudioClick = {
                            audioPocketId = pocket.videoId
                        },
                        modifier = Modifier
                            .absoluteOffset {
                                IntOffset(
                                    x = (pocket.x * boardWidthPx).roundToInt(),
                                    y = (pocket.y * boardHeightPx).roundToInt(),
                                )
                            }
                            .size(
                                width = maxWidth * pocket.width,
                                height = maxHeight * pocket.height,
                            ),
                    )
                }
            }
        }
    }

    val selectedAudioPocket = audioPocketId?.let { videoId ->
        pockets.firstOrNull { it.videoId == videoId }
    }

    if (selectedAudioPocket != null) {
        val audioState = playbackStates[selectedAudioPocket.videoId]
            ?: PlaybackState(videoId = selectedAudioPocket.videoId)
        val title = videos.findUsableVideo(selectedAudioPocket.videoId)?.title
            ?: selectedAudioPocket.videoId

        AlertDialog(
            onDismissRequest = {
                audioPocketId = null
            },
            title = {
                Text("소리: $title")
            },
            text = {
                Column {
                    Text("음량 ${audioState.volume}%")
                    Slider(
                        value = audioState.volume.toFloat(),
                        onValueChange = { value ->
                            onPlaybackStateChange(
                                audioState.withVolume(value.roundToInt())
                            )
                        },
                        valueRange = 0f..100f,
                    )
                    TextButton(
                        onClick = {
                            onPlaybackStateChange(audioState.toggleMute())
                        },
                    ) {
                        Text(
                            if (audioState.muted) {
                                "음소거 해제"
                            } else {
                                "음소거"
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { audioPocketId = null }) {
                    Text("닫기")
                }
            },
        )
    }
}

@Composable
private fun PocketWindow(
    pocket: PocketState,
    video: Video?,
    playbackState: PlaybackState,
    pauseAllRequest: Int,
    boardWidthPx: Float,
    boardHeightPx: Float,
    onPlaybackStateChange: (PlaybackState) -> Unit,
    onPocketChange: (PocketState) -> Unit,
    onAudioClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestPocket = rememberUpdatedState(pocket)
    val latestOnPocketChange = rememberUpdatedState(onPocketChange)

    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = video?.title ?: pocket.videoId,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(
                            pocket.videoId,
                            boardWidthPx,
                            boardHeightPx,
                        ) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val current = latestPocket.value
                                if (boardWidthPx > 0f && boardHeightPx > 0f) {
                                    latestOnPocketChange.value(
                                        current.moveTo(
                                            newX = current.x +
                                                    dragAmount.x / boardWidthPx,
                                            newY = current.y +
                                                    dragAmount.y / boardHeightPx,
                                        )
                                    )
                                }
                            }
                        }
                        .padding(8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = {
                        onPocketChange(
                            pocket.copy(status = PocketStatus.MINIMIZED)
                        )
                    },
                ) {
                    Text("최소화")
                }
                TextButton(
                    onClick = {
                        onPocketChange(
                            pocket.copy(status = PocketStatus.CLOSED)
                        )
                    },
                ) {
                    Text("닫기")
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (video == null) {
                    Text("이 영상을 찾을 수 없습니다.")
                } else {
                    SingleVideoPlayer(
                        video = video,
                        playbackState = playbackState,
                        onPlaybackStateChange = onPlaybackStateChange,
                        pauseAllRequest = pauseAllRequest,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAudioClick) {
                    Text("소리")
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(
                            pocket.videoId,
                            boardWidthPx,
                            boardHeightPx,
                        ) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val current = latestPocket.value
                                if (boardWidthPx > 0f && boardHeightPx > 0f) {
                                    latestOnPocketChange.value(
                                        current.resizeTo(
                                            newWidth = current.width +
                                                    dragAmount.x / boardWidthPx,
                                            newHeight = current.height +
                                                    dragAmount.y / boardHeightPx,
                                        )
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = "크기 조절 ↘",
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}
