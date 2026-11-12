package com.kwhysee.pocketmix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WorkspaceScreen(
    videos: List<Video>,
    pockets: List<PocketState>,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visiblePockets = pockets.visiblePockets()
    val frontPocket = visiblePockets.lastOrNull()
    val currentVideo = frontPocket?.let { pocket ->
        videos.findUsableVideo(pocket.videoId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "작업 공간",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(text = "열린 Pocket ${visiblePockets.size}개")

        Button(onClick = onBackToLibrary) {
            Text("영상 추가")
        }

        if (currentVideo == null) {
            Text("재생할 영상을 찾을 수 없습니다.")
        } else {
            Text(
                text = "현재 영상: ${currentVideo.title}",
                style = MaterialTheme.typography.titleMedium,
            )
            SingleVideoPlayer(
                video = currentVideo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            )
        }
    }
}