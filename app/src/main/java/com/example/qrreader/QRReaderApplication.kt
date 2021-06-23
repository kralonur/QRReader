package com.example.qrreader

import android.app.Application
import timber.log.Timber

class QRReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}