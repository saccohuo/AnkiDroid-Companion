package com.ankidroid.companion

import android.content.Context
import android.content.SharedPreferences

enum class FieldMode {
    BOTH,
    QUESTION_ONLY,
    ANSWER_ONLY
}

object UserPreferences {
    private const val PREFS_NAME = "companion_prefs"
    private const val KEY_FIELD_MODE = "field_mode"
    private const val KEY_TEMPLATE_FILTER = "template_filter"
    private const val KEY_MAX_LINES = "content_max_lines"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveFieldMode(context: Context, mode: FieldMode) {
        prefs(context).edit().putString(KEY_FIELD_MODE, mode.name).apply()
    }

    fun getFieldMode(context: Context): FieldMode {
        val stored = prefs(context).getString(KEY_FIELD_MODE, null)
        return stored?.let {
            runCatching { FieldMode.valueOf(it) }.getOrNull()
        } ?: FieldMode.BOTH
    }

    fun saveTemplateFilter(context: Context, allowedTemplates: Set<TemplateKey>) {
        val asStrings = allowedTemplates.map { "${it.modelId}:${it.ord}" }.toSet()
        prefs(context).edit().putStringSet(KEY_TEMPLATE_FILTER, asStrings).apply()
    }

    fun getTemplateFilter(context: Context): Set<TemplateKey> {
        val stored = prefs(context).getStringSet(KEY_TEMPLATE_FILTER, emptySet()) ?: emptySet()
        val parsed = stored.mapNotNull { token ->
            if (token.contains(":")) {
                val parts = token.split(":")
                if (parts.size == 2) {
                    val mid = parts[0].toLongOrNull()
                    val ord = parts[1].toIntOrNull()
                    if (mid != null && ord != null && ord >= 0) {
                        TemplateKey(mid, ord)
                    } else null
                } else null
            } else {
                // Backward compatibility: only ord stored
                token.toIntOrNull()?.let { if (it >= 0) TemplateKey(-1, it) else null }
            }
        }.toSet()
        return parsed
    }

    fun saveContentMaxLines(context: Context, lines: Int) {
        val value = lines.coerceIn(1, 50)
        prefs(context).edit().putInt(KEY_MAX_LINES, value).apply()
    }

    fun getContentMaxLines(context: Context): Int {
        val stored = prefs(context).getInt(KEY_MAX_LINES, -1)
        return if (stored in 1..50) stored else 4
    }
}
