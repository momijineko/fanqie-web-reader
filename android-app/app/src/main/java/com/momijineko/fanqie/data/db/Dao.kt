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

    @Query("SELECT * FROM books WHERE name LIKE '%' || :key || '%' OR author LIKE '%' || :key || '%'")
    suspend fun search(key: String): List<BookEntity>
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

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE groupId = :id")
    suspend fun getById(id: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity)

    @Query("DELETE FROM groups WHERE groupId = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 12): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
