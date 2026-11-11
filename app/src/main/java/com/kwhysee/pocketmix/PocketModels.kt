package com.kwhysee.pocketmix

data class Video(
    val videoId: String,
    val title: String,
    val fileLocation: String,
    val durationMs: Long,
)

data class PlaybackState(
    val videoId: String,
    val positionMs: Long = 0L,
    val volume: Int = 100,
    val muted: Boolean = false,
)

enum class PocketStatus {
    OPEN,
    MINIMIZED,
    CLOSED,
}

data class PocketState(
    val videoId: String,
    val x: Float = 0.05f,
    val y: Float = 0.08f,
    val width: Float = 0.85f,
    val height: Float = 0.38f,
    val order: Int = 0,
    val status: PocketStatus = PocketStatus.OPEN,
)