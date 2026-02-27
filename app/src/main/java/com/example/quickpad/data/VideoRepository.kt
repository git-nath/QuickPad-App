package com.example.quickpad.data

import kotlinx.coroutines.flow.Flow

class VideoRepository(private val dao: VideoDao) {
    val videos: Flow<List<VideoEntity>> = dao.getAllVideos()

    suspend fun saveVideo(uri: String, caption: String) {
        dao.insertVideo(VideoEntity(uri = uri, caption = caption))
    }
}
