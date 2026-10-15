package com.ntuli.apexledger

import android.app.Application
import com.ntuli.apexledger.util.ThemeMode

class ApexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeMode.apply(this)
        com.ntuli.apexledger.util.NotificationCenter.init(this)
    }
}
