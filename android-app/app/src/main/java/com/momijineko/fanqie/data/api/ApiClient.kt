package com.momijineko.fanqie.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: String) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    private fun buildUrl(path: String, params: Map<String, String> = emptyMap()): okhttp3.HttpUrl {
        val builder = url(path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(buildUrl(path, params)).build()
        client.newCall(req).execute().use { resp ->
            JSONObject(resp.body?.string() ?: "{}")
        }
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(path))
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            JSONObject(resp.body?.string() ?: "{}")
        }
    }

    private suspend fun delete(path: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(path)).delete().build()
        client.newCall(req).execute().use { resp ->
            JSONObject(resp.body?.string() ?: "{}")
        }
    }

    suspend fun search(key: String, tabType: Int = 3, offset: Int = 0): List<BookInfo> {
        val data = get("/api/search", mapOf("key" to key, "tab_type" to tabType.toString(), "offset" to offset.toString()))
        if (data.optInt("code") != 200) return emptyList()
        val arr = data.optJSONArray("data") ?: return emptyList()
        val books = mutableListOf<BookInfo>()
        for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            books.add(BookInfo(
                bookId = b.optString("BookID"),
                name = b.optString("Name"),
                author = b.optString("Author"),
                desc = b.optString("Desc"),
                thumbUrl = b.optString("ThumbUrl"),
                chapterCount = b.optString("ChapterCount"),
                category = b.optString("Category"),
                score = b.optString("Score"),
                wordCount = b.optLong("WordCount"),
                status = b.optString("Status"),
                tags = b.optString("Tags"),
                readCount = b.optLong("ReadCount"),
            ))
        }
        return books
    }

    suspend fun detail(bookId: String): BookDetail? {
        val data = get("/api/detail", mapOf("book_id" to bookId))
        if (data.optInt("code") != 200) return null
        val d = data.optJSONObject("data")?.optJSONObject("data") ?: return null
        return BookDetail(
            bookId = bookId,
            title = d.optString("title", d.optString("book_name", "")),
            author = d.optString("author", d.optString("writer", "")),
            thumbUrl = d.optString("thumb_url", d.optString("audio_thumb_uri", "")),
            abstractText = d.optString("abstract", ""),
            authorId = d.optString("author_id", d.optString("bind_author_ids", "")),
            category = d.optString("category", ""),
            score = d.optString("score", ""),
            wordNumber = d.optString("word_number", ""),
            creationStatus = d.optString("creation_status", ""),
            originalBookName = d.optString("original_book_name", ""),
            aliasName = d.optString("book_flight_alias_name", ""),
            chapterNumber = d.optString("chapter_number", d.optString("serial_count", "")),
        )
    }

    suspend fun chapters(bookId: String): List<ChapterInfo> {
        val data = get("/api/chapters", mapOf("book_id" to bookId))
        if (data.optInt("code") != 200) return emptyList()
        val arr = data.optJSONArray("data") ?: return emptyList()
        val chapters = mutableListOf<ChapterInfo>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            chapters.add(ChapterInfo(
                chapterId = c.optString("ChapterID"),
                name = c.optString("Name"),
                order = c.optString("Order", ""),
                updateTime = c.optLong("UpdateTime", 0),
                volumeName = c.optString("volume_name", ""),
            ))
        }
        return chapters
    }

    suspend fun content(chapterId: String): ChapterContent? {
        val data = get("/api/content", mapOf("chapter_id" to chapterId))
        if (data.optInt("code") != 200) return null
        val d = data.optJSONObject("data") ?: return null
        val paragraphs = mutableListOf<String>()
        val arr = d.optJSONArray("Paragraphs")
        if (arr != null) {
            for (i in 0 until arr.length()) paragraphs.add(arr.getString(i))
        }
        return ChapterContent(
            chapterId = chapterId,
            title = d.optString("Title", ""),
            paragraphs = paragraphs,
            authorSpeak = d.optString("AuthorSpeak", ""),
        )
    }

    suspend fun saveCookie(cookie: String): Boolean {
        val body = JSONObject().put("cookie", cookie)
        val data = post("/api/user/cookie", body)
        return data.optInt("code") == 200
    }

    suspend fun deleteCookie(): Boolean {
        val data = delete("/api/user/cookie")
        return data.optInt("code") == 200
    }

    suspend fun getUserInfo(): UserInfo? {
        val data = get("/api/user/info")
        if (data.optInt("code") != 200) return null
        val d = data.optJSONObject("data") ?: return null
        return UserInfo(
            userName = d.optString("user_name"),
            avatarUrl = d.optString("avatar_url"),
            userId = d.optString("user_id"),
        )
    }

    suspend fun getBookshelf(): List<BookshelfItem> {
        val data = get("/api/user/bookshelf")
        if (data.optInt("code") != 200) return emptyList()
        val arr = data.optJSONArray("data") ?: return emptyList()
        val items = mutableListOf<BookshelfItem>()
        for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            items.add(BookshelfItem(
                bookId = b.optString("BookID"),
                name = b.optString("Name"),
                thumbUrl = b.optString("ThumbUrl"),
                desc = b.optString("Desc"),
                chapterCount = b.optInt("ChapterCount", 0),
                status = b.optString("Status"),
                groupId = b.optString("GroupID"),
                groupName = b.optString("GroupName"),
                lastReadChapter = b.optString("LastReadChapter"),
                lastReadTime = b.optLong("LastReadTime", 0),
                readChapterIdx = b.optInt("ReadChapterIdx", -1),
                readItemId = b.optString("ReadItemId", "0"),
                lastUpdateTime = b.optLong("LastUpdateTime", 0),
                updateStopped = b.optBoolean("UpdateStopped", false) || b.optString("update_stop") == "1",
            ))
        }
        return items
    }

    suspend fun updateProgress(bookId: String, itemId: String, index: Int): Boolean {
        val body = JSONObject()
            .put("book_id", bookId)
            .put("item_id", itemId)
            .put("index", index)
        val data = post("/api/user/progress", body)
        return data.optInt("code") == 200
    }

    suspend fun addToShelf(bookId: String): Boolean {
        val body = JSONObject().put("book_id", bookId)
        val data = post("/api/user/bookshelf/add", body)
        return data.optInt("code") == 200
    }

    suspend fun removeFromShelf(bookId: String): Boolean {
        val body = JSONObject().put("book_id", bookId)
        val data = post("/api/user/bookshelf/remove", body)
        return data.optInt("code") == 200
    }

    suspend fun health(): Boolean {
        return try {
            val data = get("/api/health")
            data.optString("status") == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun moveToGroup(bookId: String, groupId: String, groupName: String): Boolean {
        val body = JSONObject().put("book_id", bookId)
        if (groupId.isNotEmpty()) {
            body.put("group_id", groupId)
            body.put("group_name", groupName)
        }
        val data = post("/api/user/bookshelf/move", body)
        return data.optInt("code") == 200
    }

    suspend fun getComments(bookId: String, chapterId: String = "", offset: Int = 0, count: Int = 20): List<CommentInfo> {
        val params = mutableMapOf("book_id" to bookId, "offset" to offset.toString(), "count" to count.toString())
        if (chapterId.isNotEmpty()) params["chapter_id"] = chapterId
        val data = get("/api/comments", params)
        if (data.optInt("code") != 200) return emptyList()
        val d = data.optJSONObject("data")
        val arr = d?.optJSONArray("comment") ?: d?.optJSONArray("data") ?: return emptyList()
        val comments = mutableListOf<CommentInfo>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            comments.add(parseComment(c))
        }
        return comments
    }

    private fun parseComment(c: JSONObject): CommentInfo {
        val ui = c.optJSONObject("user_info")
        val replies = mutableListOf<CommentInfo>()
        val replyArr = c.optJSONArray("reply_list") ?: c.optJSONArray("reply_comment") ?: c.optJSONArray("child_comments")
        if (replyArr != null) {
            for (i in 0 until replyArr.length()) {
                val r = replyArr.optJSONObject(i) ?: continue
                replies.add(parseComment(r))
            }
        }
        return CommentInfo(
            userName = ui?.optString("user_name") ?: ui?.optString("nick_name") ?: c.optString("user_name", "匿名"),
            avatarUrl = ui?.optString("user_avatar") ?: ui?.optString("avatar_url") ?: c.optString("avatar_url", ""),
            content = c.optString("text", c.optString("content", "")),
            createTime = c.optLong("create_timestamp", c.optLong("create_time", 0)),
            diggCount = c.optInt("digg_count", 0),
            replies = replies,
        )
    }

    suspend fun getParaCommentCounts(chapterId: String, bookId: String = ""): Map<Int, Int> {
        val body = JSONObject().put("chapter_id", chapterId)
        if (bookId.isNotEmpty()) body.put("book_id", bookId)
        val data = post("/api/paragraph_comment_counts", body)
        if (data.optInt("code") != 200) return emptyMap()
        val d = data.optJSONObject("data") ?: return emptyMap()
        val result = mutableMapOf<Int, Int>()
        val keys = d.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key.toInt()] = d.optInt(key, 0)
        }
        return result
    }

    suspend fun getParaComments(chapterId: String, paragraphIdx: Int, bookId: String = ""): List<ParaComment> {
        val body = JSONObject()
            .put("chapter_id", chapterId)
            .put("paragraph_index", paragraphIdx)
        if (bookId.isNotEmpty()) body.put("book_id", bookId)
        val data = post("/api/paragraph_comments", body)
        if (data.optInt("code") != 200) return emptyList()
        val arr = data.optJSONArray("data") ?: return emptyList()
        val comments = mutableListOf<ParaComment>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val replies = mutableListOf<ParaComment>()
            val replyArr = c.optJSONArray("reply_list") ?: c.optJSONArray("reply_comment") ?: c.optJSONArray("child_comments")
            if (replyArr != null) {
                for (j in 0 until replyArr.length()) {
                    val r = replyArr.optJSONObject(j) ?: continue
                    replies.add(ParaComment(
                        userName = r.optString("user_name", "匿名"),
                        avatarUrl = r.optString("avatar_url", ""),
                        content = r.optString("text", r.optString("content", "")),
                        createTime = r.optLong("create_timestamp", r.optLong("create_time", 0)),
                        diggCount = r.optInt("digg_count", 0),
                    ))
                }
            }
            comments.add(ParaComment(
                userName = c.optString("user_name", "匿名"),
                avatarUrl = c.optString("avatar_url", ""),
                content = c.optString("text", c.optString("content", "")),
                createTime = c.optLong("create_timestamp", c.optLong("create_time", 0)),
                diggCount = c.optInt("digg_count", 0),
                replies = replies,
            ))
        }
        return comments
    }

    suspend fun getAuthorBooks(authorId: String): List<BookInfo> {
        val data = get("/api/author_books", mapOf("author_id" to authorId))
        if (data.optInt("code") != 200) return emptyList()
        val arr = data.optJSONArray("data") ?: return emptyList()
        val books = mutableListOf<BookInfo>()
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            books.add(BookInfo(
                bookId = b.optString("BookID"),
                name = b.optString("Name"),
                author = b.optString("Author"),
                desc = b.optString("Desc"),
                thumbUrl = b.optString("ThumbUrl"),
                chapterCount = b.optString("ChapterCount"),
                category = b.optString("Category"),
                score = b.optString("Score"),
                wordCount = b.optLong("WordCount"),
                status = b.optString("Status"),
                tags = b.optString("Tags"),
                readCount = b.optLong("ReadCount"),
            ))
        }
        return books
    }

    suspend fun getVersion(): String {
        return try {
            val data = get("/api/version")
            data.optString("version", "unknown")
        } catch (e: Exception) { "unknown" }
    }
}
