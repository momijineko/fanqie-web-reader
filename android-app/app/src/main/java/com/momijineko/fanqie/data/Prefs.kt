package com.momijineko.fanqie.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("fanqie", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", "http://192.168.1.100:8080")!!
        set(value) = sp.edit().putString("server_url", value).apply()

    var fontSize: Int
        get() = sp.getInt("font_size", 18)
        set(value) = sp.edit().putInt("font_size", value).apply()

    var lineHeight: Float
        get() = sp.getFloat("line_height", 1.8f)
        set(value) = sp.edit().putFloat("line_height", value).apply()

    var fontFamily: String
        get() = sp.getString("font_family", "sans")!!
        set(value) = sp.edit().putString("font_family", value).apply()

    var readMode: String
        get() = sp.getString("read_mode", "page")!!
        set(value) = sp.edit().putString("read_mode", value).apply()

    var readingTheme: String
        get() = sp.getString("reading_theme", "default")!!
        set(value) = sp.edit().putString("reading_theme", value).apply()

    var einkMode: Boolean
        get() = sp.getBoolean("eink_mode", true)
        set(value) = sp.edit().putBoolean("eink_mode", value).apply()

    var isLoggedIn: Boolean
        get() = sp.getBoolean("logged_in", false)
        set(value) = sp.edit().putBoolean("logged_in", value).apply()

    var cachedUserName: String
        get() = sp.getString("cached_user_name", "")!!
        set(value) = sp.edit().putString("cached_user_name", value).apply()

    var cachedUserAvatar: String
        get() = sp.getString("cached_user_avatar", "")!!
        set(value) = sp.edit().putString("cached_user_avatar", value).apply()

    var searchTab: Int
        get() = sp.getInt("search_tab", 3)
        set(value) = sp.edit().putInt("search_tab", value).apply()

    fun getString(key: String, default: String = ""): String = sp.getString(key, default)!!
    fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false): Boolean = sp.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
}
