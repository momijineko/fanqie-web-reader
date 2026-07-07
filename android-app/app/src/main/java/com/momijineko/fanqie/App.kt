package com.momijineko.fanqie

import android.app.Application
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import androidx.recyclerview.widget.RecyclerView
import com.momijineko.fanqie.data.api.ApiClient
import com.momijineko.fanqie.data.db.AppDatabase
import com.momijineko.fanqie.data.Prefs

class App : Application() {
    lateinit var api: ApiClient
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        db = AppDatabase.getInstance(this)
        api = ApiClient(prefs.serverUrl)
    }

    fun reconnectApi() {
        api = ApiClient(prefs.serverUrl)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
