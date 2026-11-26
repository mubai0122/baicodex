/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:15
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 会话持久化：%APPDATA%/Baicodex/sessions.json
 */

package b.ai.desktop.core.store

import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.model.Session
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class SessionStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File = File(ConfigStore.appDir(), "sessions.json")
    private val serializer = ListSerializer(Session.serializer())

    fun load(): List<Session> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrElse { emptyList() }
    }

    fun save(sessions: List<Session>) {
        runCatching { file.writeText(json.encodeToString(serializer, sessions)) }
    }
}
