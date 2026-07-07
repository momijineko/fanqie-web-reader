package com.momijineko.fanqie.data.api

data class BookInfo(
    val bookId: String,
    val name: String,
    val author: String,
    val desc: String,
    val thumbUrl: String,
    val chapterCount: String,
    val category: String,
    val score: String,
    val wordCount: Long,
    val status: String,
    val tags: String,
    val readCount: Long,
)

data class BookDetail(
    val bookId: String,
    val title: String,
    val author: String,
    val thumbUrl: String,
    val abstractText: String,
    val authorId: String,
    val category: String,
    val score: String,
    val wordNumber: String,
    val creationStatus: String,
)

data class ChapterInfo(
    val chapterId: String,
    val name: String,
    val order: String,
    val updateTime: Long,
)

data class ChapterContent(
    val chapterId: String,
    val title: String,
    val paragraphs: List<String>,
    val authorSpeak: String,
)

data class UserInfo(
    val userName: String,
    val avatarUrl: String,
    val userId: String,
)

data class BookshelfItem(
    val bookId: String,
    val name: String,
    val thumbUrl: String,
    val desc: String,
    val chapterCount: Int,
    val status: String,
    val groupId: String,
    val groupName: String,
    val lastReadChapter: String,
    val lastReadTime: Long,
    val readChapterIdx: Int,
    val readItemId: String,
)
