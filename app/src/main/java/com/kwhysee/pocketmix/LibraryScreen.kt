package com.kwhysee.pocketmix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    val videos = listOf(
        Video("sea-001", "오후의 해변", "sample/sea.mp4", 380_000L),
        Video("city-002", "도시의 저녁", "sample/city.mp4", 252_000L),
    )

    var pockets by remember {
        mutableStateOf(emptyList<PocketState>())
    }

    var searchText by remember {
        mutableStateOf("")
    }

    val filteredVideos = videos.filter { video ->
        video.title.contains(searchText.trim(), ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "내 영상",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(text = "저장된 영상 ${videos.size}개")

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
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
                    pockets = pockets.openPocket(video.videoId)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}