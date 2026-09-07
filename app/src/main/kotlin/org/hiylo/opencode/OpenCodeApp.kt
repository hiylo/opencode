/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : OpenCodeApp.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode

import android.app.Application
import android.content.ComponentCallbacks2
import dagger.hilt.android.HiltAndroidApp
import org.hiylo.opencode.data.repository.DiagnosticLogRepository
import org.hiylo.opencode.logging.AppLogger
import org.hiylo.opencode.ml.MnnLlm
import javax.inject.Inject

/**
 * OpenCode Application
 * Entry point for Hilt dependency injection
 */
@HiltAndroidApp
class OpenCodeApp : Application() {
    @Inject lateinit var diagnosticLogRepository: DiagnosticLogRepository

    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(diagnosticLogRepository)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                AppLogger.recordCrash(thread, error)
            } finally {
                previousHandler?.uncaughtException(thread, error)
            }
        }
        // Release the memory-heavy on-device LLM when the system needs memory back.
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                    MnnLlm.release()
                }
            }

            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}

            override fun onLowMemory() {
                MnnLlm.release()
            }
        })
    }
}
