package com.xiaohongshu.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore("xhs_search_history")

/** 顺序敏感的字符串列表序列化器（用 JSON 数组而非 Set，避免顺序丢失）。 */
private val stringListSerializer = ListSerializer(String.serializer())

/**
 * 搜索历史（B2）—— 纯本地能力（契约 §11）。
 *
 * 规则：最新在前、去重、上限 [MAX_ITEMS] 条；为空时 B2「历史记录」区块**整块隐藏**；
 * 点 🗑 清空。
 */
class SearchHistoryStore(private val context: Context) {

    private val key = stringPreferencesKey("history")

    /** 历史词条，最新在前。 */
    val history: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun snapshot(): List<String> = history.first()

    /** 记录一次搜索（词已存在则提到最前）。 */
    suspend fun add(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = decode(prefs[key])
            val next = (listOf(trimmed) + current.filterNot { it == trimmed }).take(MAX_ITEMS)
            prefs[key] = Json.encodeToString(stringListSerializer, next)
        }
    }

    suspend fun clear() {
        context.searchHistoryDataStore.edit { it.remove(key) }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { Json.decodeFromString(stringListSerializer, raw) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_ITEMS = 20
    }
}
