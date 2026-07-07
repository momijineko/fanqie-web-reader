package com.momijineko.fanqie.data.db

import androidx.room.*

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE bookId = :id")
    suspend fun getById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("DELETE FROM books WHERE bookId = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE chapterId = :id")
    suspend fun getById(id: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ProgressEntity>
}
