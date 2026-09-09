/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : ServerConfig.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * Server Configuration - stored server connection details
 */
@Serializable
data class ServerConfig(
    val id: String, // UUID
    val url: String, // e.g. http://192.168.1.100:4096
    val username: String = "opencode",
    val password: String? = null,
    val name: String? = null, // User-friendly name
    val autoConnect: Boolean = false,
    val lastConnected: Long? = null,
    val isHealthy: Boolean = false,
    // SSH 隧道（可选）：配置后通过 SSH 本地端口转发连接并重启服务。
    val sshPort: Int = 22,
    val sshUsername: String = "",
    val sshPassword: String? = null,
) {
    val displayName: String
        get() = name ?: url

    /** 是否启用 SSH 隧道（以是否填写了 SSH 用户名判定）。 */
    val useSsh: Boolean
        get() = sshUsername.isNotBlank()

    /** OpenCode 服务端口（显式端口，否则回退 http/https 默认端口）。 */
    val openCodePort: Int
        get() = try {
            val parsed = java.net.URL(url)
            val explicitPort = parsed.port
            if (explicitPort != -1) explicitPort else parsed.defaultPort
        } catch (e: Exception) {
            url.substringAfterLast(":").toIntOrNull() ?: 80
        }

    val host: String
        get() = try {
            java.net.URL(url).host
        } catch (e: Exception) {
            url.substringAfter("://").substringBefore(":")
        }

    val port: Int
        get() = openCodePort
}

/**
 * Server Health - result of health check
 */
@Serializable
data class ServerHealth(
    val healthy: Boolean,
    val version: String? = null
)
