package com.momijineko.fanqie.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val bookId: String,
    val name: String,
    val author: String,
    val thumbUrl: String,
    val desc: String,
    val chapterCount: Int = 0,
    val status: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val chapterId: String,
    val bookId: String,
    val title: String,
    val paragraphsJson: String,
    val authorSpeak: String = "",
    val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterIdx: Int,
    val chapterId: String,
    val chapterName: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
