package com.kwhysee.pocketmix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

        if (visiblePockets.isEmpty()) {
            Text("열린 Pocket이 없습니다.")
        }

        visiblePockets.forEach { pocket ->
            val title = videos.findUsableVideo(pocket.videoId)?.title
                ?: "영상 정보를 찾을 수 없습니다."

            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}