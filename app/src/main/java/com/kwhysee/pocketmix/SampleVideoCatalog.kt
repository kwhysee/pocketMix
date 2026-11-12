package com.kwhysee.pocketmix

object SampleVideoCatalog {
    val videos: List<Video> = listOf(
        Video(
            videoId = "sample-001",
            title = "폭포",
            fileLocation = "asset:///videos/pm_sample_one.mp4",
            durationMs = 10_000L,
        ),
        Video(
            videoId = "sample-002",
            title = "숲길 주행",
            fileLocation = "asset:///videos/pm_sample_two.mp4",
            durationMs = 10_000L,
        ),
    )
}