/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : SettingsViewModel.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.opencode.data.repository.SettingsRepository
import org.hiylo.opencode.ml.MnnLlm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _modelDownloading = MutableStateFlow(false)
    val modelDownloading: StateFlow<Boolean> = _modelDownloading

    private val _modelExtracting = MutableStateFlow(false)
    val modelExtracting: StateFlow<Boolean> = _modelExtracting

    private val _modelDownloadProgress = MutableStateFlow(0)
    val modelDownloadProgress: StateFlow<Int> = _modelDownloadProgress

    private val _modelReady = MutableStateFlow(false)
    val modelReady: StateFlow<Boolean> = _modelReady

    private val _modelDownloadFailed = MutableStateFlow(false)
    val modelDownloadFailed: StateFlow<Boolean> = _modelDownloadFailed

    init {
        prepareModel()
    }

    /**
     * Ensures the on-device model is available: already on disk → ready; bundled in app
     * assets (debug builds ship the weights) → extract in the background; otherwise the
     * download button is shown as a fallback.
     */
    private fun prepareModel() {
        viewModelScope.launch {
            if (MnnLlm.modelDirectory(context) != null) {
                _modelReady.value = true
                return@launch
            }
            if (!runCatching { MnnLlm.hasBundledModel(context) }.getOrDefault(false)) return@launch
            _modelDownloading.value = true
            _modelExtracting.value = true
            _modelDownloadFailed.value = false
            val ok = MnnLlm.extractBundledModel(context) { percent ->
                _modelDownloadProgress.value = percent
            }
            _modelDownloading.value = false
            _modelExtracting.value = false
            _modelReady.value = ok
            if (!ok) {
                _modelDownloadFailed.value = true
            }
        }
    }

    /** Re-checks whether the on-device model is present on disk (e.g. after app restart). */
    fun refreshModelStatus() {
        _modelReady.value = MnnLlm.modelDirectory(context) != null
        if (_modelReady.value) _modelDownloadFailed.value = false
    }

    /**
     * Downloads the on-device model from GitHub Releases and reports progress to the UI.
     * Safe to call repeatedly; skips if already downloaded or currently busy.
     */
    fun downloadModel() {
        if (_modelDownloading.value || _modelReady.value) return
        viewModelScope.launch {
            _modelDownloading.value = true
            _modelExtracting.value = false
            _modelDownloadProgress.value = 0
            _modelDownloadFailed.value = false
            val ok = MnnLlm.downloadModel(context) { percent ->
                _modelDownloadProgress.value = percent
            }
            _modelDownloading.value = false
            _modelReady.value = ok
            if (!ok) {
                _modelDownloadFailed.value = true
            }
        }
    }
    
    val appLanguage = settingsRepository.appLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val appTheme = settingsRepository.appTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "system"
    )

    val dynamicColor = settingsRepository.dynamicColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsRepository.DEFAULT_DYNAMIC_COLOR,
    )

    val chatFontSize = settingsRepository.chatFontSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "medium"
    )

    val notificationsEnabled = settingsRepository.notificationsEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val initialMessageCount = settingsRepository.initialMessageCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 50
    )

    val messageHistoryResponseLimitMb = settingsRepository.messageHistoryResponseLimitMb.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 24,
    )

    val recentDirectoryCount = settingsRepository.recentDirectoryCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 20,
    )

    val codeWordWrap = settingsRepository.codeWordWrap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val confirmBeforeSend = settingsRepository.confirmBeforeSend.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val amoledDark = settingsRepository.amoledDark.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val accentColor = settingsRepository.accentColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "indigo"
    )

    fun setAccentColor(accent: String) {
        viewModelScope.launch {
            settingsRepository.setAccentColor(accent)
        }
    }

    val compactMessages = settingsRepository.compactMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val collapseTools = settingsRepository.collapseTools.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val expandReasoning = settingsRepository.expandReasoning.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    val showTurnDividers = settingsRepository.showTurnDividers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    val hapticFeedback = settingsRepository.hapticFeedback.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val hapticDurationMillis = settingsRepository.hapticDurationMillis.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 30,
    )

    val hapticAmplitude = settingsRepository.hapticAmplitude.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 160,
    )

    val reconnectMode = settingsRepository.reconnectMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "normal"
    )

    val backgroundWakeLock = settingsRepository.backgroundWakeLock.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    val keepScreenOn = settingsRepository.keepScreenOn.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val compressImageAttachments = settingsRepository.compressImageAttachments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val imageAttachmentMaxLongSide = settingsRepository.imageAttachmentMaxLongSide.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1440
    )

    val imageAttachmentWebpQuality = settingsRepository.imageAttachmentWebpQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 60
    )

    val silentNotifications = settingsRepository.silentNotifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val terminalFontSize = settingsRepository.terminalFontSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 13f
    )

    val showTerminalPanelHint = settingsRepository.showTerminalPanelHint.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(languageCode)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setChatFontSize(size: String) {
        viewModelScope.launch {
            settingsRepository.setChatFontSize(size)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setInitialMessageCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.setInitialMessageCount(count)
        }
    }

    fun setMessageHistoryResponseLimitMb(limitMb: Int) {
        viewModelScope.launch {
            settingsRepository.setMessageHistoryResponseLimitMb(limitMb)
        }
    }

    fun setRecentDirectoryCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.setRecentDirectoryCount(count)
        }
    }

    fun setCodeWordWrap(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCodeWordWrap(enabled)
        }
    }

    fun setConfirmBeforeSend(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setConfirmBeforeSend(enabled)
        }
    }

    fun setAmoledDark(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAmoledDark(enabled)
        }
    }

    fun setCompactMessages(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCompactMessages(enabled)
        }
    }

    fun setCollapseTools(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCollapseTools(enabled)
        }
    }

    fun setExpandReasoning(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setExpandReasoning(enabled) }
    }

    fun setShowTurnDividers(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowTurnDividers(enabled) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHapticFeedback(enabled)
        }
    }

    fun setHapticPattern(durationMillis: Int, amplitude: Int) {
        viewModelScope.launch {
            settingsRepository.setHapticPattern(durationMillis, amplitude)
        }
    }

    fun setReconnectMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setReconnectMode(mode)
        }
    }

    fun setBackgroundWakeLock(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundWakeLock(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(enabled)
        }
    }

    fun setSilentNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSilentNotifications(enabled)
        }
    }

    fun setCompressImageAttachments(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCompressImageAttachments(enabled)
        }
    }

    fun setImageAttachmentMaxLongSide(px: Int) {
        viewModelScope.launch {
            settingsRepository.setImageAttachmentMaxLongSide(px)
        }
    }

    fun setImageAttachmentWebpQuality(quality: Int) {
        viewModelScope.launch {
            settingsRepository.setImageAttachmentWebpQuality(quality)
        }
    }

    fun setTerminalFontSize(size: Float) {
        viewModelScope.launch {
            settingsRepository.setTerminalFontSize(size)
        }
    }

    fun setShowTerminalPanelHint(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTerminalPanelHint(enabled)
        }
    }
}
