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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
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
    onBackToLibrary: () -> Unit,
    onPocketChange: (PocketState) -> Unit,
    onPocketSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openPockets = pockets
        .filter { it.status == PocketStatus.OPEN }
        .sortedBy { it.order }
    val minimizedPockets = pockets
        .filter { it.status == PocketStatus.MINIMIZED }
    val frontPocket = openPockets.lastOrNull()
    val frontVideo = frontPocket?.let { pocket ->
        videos.findUsableVideo(pocket.videoId)
    }

    // 드래그가 진행되는 동안 가장 최근의 Pocket 값과 콜백을 사용한다.
    val latestPocket = rememberUpdatedState(frontPocket)
    val latestOnPocketChange = rememberUpdatedState(onPocketChange)

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

        if (openPockets.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                openPockets.forEach { pocket ->
                    val title = videos.findUsableVideo(pocket.videoId)?.title
                        ?: pocket.videoId
                    OutlinedButton(
                        onClick = { onPocketSelect(pocket.videoId) },
                    ) {
                        Text(
                            text = if (pocket.videoId == frontPocket?.videoId) {
                                "● $title"
                            } else {
                                title
                            },
                        )
                    }
                }
            }
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

            if (frontPocket == null) {
                Text(
                    text = "열린 Pocket이 없습니다. '영상 추가'로 영상을 선택하세요.",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Card(
                    modifier = Modifier
                        .absoluteOffset {
                            IntOffset(
                                x = (frontPocket.x * boardWidthPx).roundToInt(),
                                y = (frontPocket.y * boardHeightPx).roundToInt(),
                            )
                        }
                        .size(
                            width = maxWidth * frontPocket.width,
                            height = maxHeight * frontPocket.height,
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = frontVideo?.title ?: frontPocket.videoId,
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(
                                        frontPocket.videoId,
                                        boardWidthPx,
                                        boardHeightPx,
                                    ) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val pocket = latestPocket.value
                                            if (pocket != null &&
                                                boardWidthPx > 0f &&
                                                boardHeightPx > 0f
                                            ) {
                                                latestOnPocketChange.value(
                                                    pocket.moveTo(
                                                        newX = pocket.x +
                                                                dragAmount.x / boardWidthPx,
                                                        newY = pocket.y +
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
                                        frontPocket.copy(
                                            status = PocketStatus.MINIMIZED
                                        )
                                    )
                                },
                            ) {
                                Text("최소화")
                            }
                            TextButton(
                                onClick = {
                                    onPocketChange(
                                        frontPocket.copy(
                                            status = PocketStatus.CLOSED
                                        )
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
                            if (frontVideo == null) {
                                Text("이 영상을 찾을 수 없습니다.")
                            } else {
                                SingleVideoPlayer(
                                    video = frontVideo,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .pointerInput(
                                    frontPocket.videoId,
                                    boardWidthPx,
                                    boardHeightPx,
                                ) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val pocket = latestPocket.value
                                        if (pocket != null &&
                                            boardWidthPx > 0f &&
                                            boardHeightPx > 0f
                                        ) {
                                            latestOnPocketChange.value(
                                                pocket.resizeTo(
                                                    newWidth = pocket.width +
                                                            dragAmount.x / boardWidthPx,
                                                    newHeight = pocket.height +
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
    }
}