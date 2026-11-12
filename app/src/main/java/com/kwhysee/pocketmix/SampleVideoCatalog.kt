package com.kwhysee.pocketmix

object SampleVideoCatalog {
    val videos: List<Video> = listOf(
        Video(
            videoId = "sample-001",
            title = "공원 옆 버스",
            fileLocation = "asset:///videos/pm_sample_one.mp4",
            durationMs = 10_000L,
        ),
        Video(
            videoId = "sample-002",
            title = "도로와 교통 흐름",
            fileLocation = "asset:///videos/pm_sample_two.mp4",
            durationMs = 20_000L,
        ),
    )
}