package com.kwhysee.pocketmix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(
    videos: List<Video>,
    pockets: List<PocketState>,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onOpenPocket: (String) -> Unit,
    onImportVideo: () -> Unit,
    isImportingVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    val filteredVideos = videos.filter { video ->
        video.title.contains(searchText.trim(), ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "내 영상",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(text = "저장된 영상 ${videos.size}개")

        Button(
            onClick = onImportVideo,
            enabled = !isImportingVideo,
        ) {
            Text(
                if (isImportingVideo) "영상 가져오는 중..." else "영상 가져오기"
            )
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = { Text("영상 검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = "검색 결과 ${filteredVideos.size}개")
        Text(text = "Pocket으로 선택한 영상 ${pockets.visiblePockets().size}개")

        if (filteredVideos.isEmpty()) {
            Text(text = "검색 결과가 없습니다.")
        }

        filteredVideos.forEach { video ->
            val selected = pockets.any { pocket ->
                pocket.videoId == video.videoId &&
                        pocket.status == PocketStatus.OPEN
            }

            VideoCard(
                video = video,
                selected = selected,
                onSelect = {
                    onOpenPocket(video.videoId)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}