package com.chrizzzzilla.self

import android.app.Application
import com.chrizzzzilla.self.core.SelfCore

class SelfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SelfCore.init(this)
    }
}
