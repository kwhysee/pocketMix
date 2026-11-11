package com.kwhysee.pocketmix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VideoCard(
    video: Video,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = video.durationMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = "길이 $minutes:$seconds")

            Button(
                onClick = onSelect,
                enabled = !selected,
            ) {
                Text(if (selected) "선택됨" else "Pocket에 담기")
            }
        }
    }
}