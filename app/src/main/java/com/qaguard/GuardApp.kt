package com.qaguard

import android.app.Application

class GuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.initOnce(this)
    }
}
