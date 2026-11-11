package com.kwhysee.pocketmix

object SampleVideoCatalog {
    val videos: List<Video> = listOf(
        Video(
            videoId = "sea-001",
            title = "오후의 해변",
            fileLocation = "sample/sea.mp4",
            durationMs = 380_000L,
        ),
        Video(
            videoId = "city-002",
            title = "도시의 저녁",
            fileLocation = "sample/city.mp4",
            durationMs = 252_000L,
        ),
    )
}