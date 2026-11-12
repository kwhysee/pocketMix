package com.kwhysee.pocketmix

object SampleVideoCatalog {
    val videos: List<Video> = listOf(
        Video(
            videoId = "sample-001",
            title = "홈 화면 녹화",
            fileLocation = "asset:///videos/pm_sample_one.mp4",
            durationMs = 5_657L,
        ),
        Video(
            videoId = "sample-002",
            title = "설정 화면 녹화",
            fileLocation = "asset:///videos/pm_sample_two.mp4",
            durationMs = 7_888L,
        ),
    )
}