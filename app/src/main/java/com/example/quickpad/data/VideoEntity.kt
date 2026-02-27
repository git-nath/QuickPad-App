package com.example.quickpad.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val caption: String,
    val createdAt: Long = System.currentTimeMillis()
)
