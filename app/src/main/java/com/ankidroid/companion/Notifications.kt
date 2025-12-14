package com.ankidroid.companion

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat


class Notifications {
    companion object Factory {
        fun create(): Notifications = Notifications()
    }

    fun showNotification(context: Context, card: CardInfo?, deckName: String, isSilent: Boolean) {
        Log.i("Notifications", "showNotification called")
        val builder: NotificationCompat.Builder
        val publicBuilder: NotificationCompat.Builder
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_collapsed)

        // There is a card to show, show a notification with the expanded view.
        if (card != null) {
            val fieldMode = UserPreferences.getFieldMode(context)
            val questionCollapsed = sanitizeText(card.rawQuestion, card.simpleQuestion, 140)
            val answerCollapsed = sanitizeText(card.rawAnswer, card.simpleAnswer, 220)

            val headerText = when (fieldMode) {
                FieldMode.BOTH -> questionCollapsed
                FieldMode.QUESTION_ONLY -> questionCollapsed
                FieldMode.ANSWER_ONLY -> ""
            }
            val safeHeader = if (headerText.isNotBlank()) headerText else context.getString(R.string.app_name)
            val expandedContent = when (fieldMode) {
                FieldMode.BOTH -> sanitizeText(card.rawAnswer, card.simpleAnswer, 400)
                FieldMode.QUESTION_ONLY -> ""
                FieldMode.ANSWER_ONLY -> sanitizeText(card.rawAnswer, card.simpleAnswer, 1200)
            }

            val displayTitle = if (deckName.isNotBlank()) "Anki • $deckName" else "Anki"

            val collapsedHeader = when (fieldMode) {
                FieldMode.ANSWER_ONLY -> if (answerCollapsed.isNotBlank()) answerCollapsed else ""
                else -> safeHeader
            }

            collapsedView.setTextViewText(R.id.textViewCollapsedHeader, collapsedHeader)
            collapsedView.setTextViewText(R.id.textViewCollapsedTitle, if (fieldMode == FieldMode.ANSWER_ONLY) "" else displayTitle)
            collapsedView.setViewVisibility(R.id.textViewCollapsedTitle, if (fieldMode == FieldMode.ANSWER_ONLY) View.GONE else View.VISIBLE)

            val expandedView = RemoteViews(context.packageName, R.layout.notification_expanded_full)
            val expandedHeader = when (fieldMode) {
                FieldMode.ANSWER_ONLY -> ""
                else -> safeHeader
            }
            expandedView.setTextViewText(R.id.textViewExpandedHeader, expandedHeader)
            expandedView.setViewVisibility(R.id.textViewExpandedHeader, if (fieldMode == FieldMode.ANSWER_ONLY) View.GONE else View.VISIBLE)
            expandedView.setTextViewText(R.id.textViewContent, expandedContent)
            val maxLines = UserPreferences.getContentMaxLines(context)
            expandedView.setInt(R.id.textViewContent, "setMaxLines", maxLines)
            if (expandedContent.isBlank()) {
                expandedView.setViewVisibility(R.id.textViewContent, View.GONE)
            } else {
                expandedView.setViewVisibility(R.id.textViewContent, View.VISIBLE)
            }

            val image = loadCardImage(context, card)
            applyImage(collapsedView, expandedView, image)
            collapsedView.setViewVisibility(R.id.playButtonCollapsed, View.GONE)
            expandedView.setViewVisibility(R.id.playButtonExpanded, View.GONE)

            expandedView.setOnClickPendingIntent(R.id.button1, createIntent(context,"ACTION_BUTTON_1"))
            expandedView.setOnClickPendingIntent(R.id.button2, createIntent(context,"ACTION_BUTTON_2"))
            expandedView.setOnClickPendingIntent(R.id.button3, createIntent(context,"ACTION_BUTTON_3"))
            expandedView.setOnClickPendingIntent(R.id.button4, createIntent(context,"ACTION_BUTTON_4"))

            builder = NotificationCompat.Builder(context, "channel_id")
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setContentTitle("Anki • $deckName")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(isSilent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)

            val publicText = sanitizeText(card.rawAnswer, card.simpleAnswer, 200)
            publicBuilder = NotificationCompat.Builder(context, "channel_id")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (deckName.isNotBlank()) "Anki • $deckName" else context.getString(R.string.app_name))
                .setContentText(publicText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(publicText))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        } else {
            collapsedView.setTextViewText(R.id.textViewCollapsedHeader, "Congrats! You've finished the deck!")
            collapsedView.setTextViewText(R.id.textViewCollapsedTitle, "Anki")

            val expandedView = RemoteViews(context.packageName, R.layout.notification_expanded_empty)
            expandedView.setTextViewText(R.id.textViewEmptyExpandedHeader, "Congrats! You've finished the deck!")
            expandedView.setTextViewText(R.id.textViewEmptyExpandedContent, "New notifications will arrive when it's time to study!")

            builder = NotificationCompat.Builder(context, "channel_id")
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setContentTitle("Anki")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(isSilent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(false)

            publicBuilder = NotificationCompat.Builder(context, "channel_id")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Anki")
                .setContentText("Congrats! You've finished the deck!")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Congrats! You've finished the deck!"))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        val notification: Notification = builder.setPublicVersion(publicBuilder.build()).build()

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(1) // Cancel the current notification before sending a new one
        notificationManager.notify(1, notification)
    }

    private fun createIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java)
        intent.action = action
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createIntent(context: Context, action: String, sound: String): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java)
        intent.action = action
        intent.putExtra("sound", sound)
        return PendingIntent.getBroadcast(context, sound.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun sanitizeText(raw: String?, fallback: String?, maxChars: Int): String {
        val source = when {
            !raw.isNullOrBlank() -> raw
            !fallback.isNullOrBlank() -> fallback
            else -> ""
        }
        val withoutSound = source.replace(Regex("\\[sound:[^\\]]+\\]"), "")
        val withoutImages = withoutSound.replace(Regex("(?is)<img[^>]*>"), " ")
        val withoutUrls = withoutImages.replace(Regex("https?://\\S+"), "")
        val withoutLinks = withoutUrls.replace(Regex("(?is)<a[^>]*>.*?</a>"), " ")
        val withoutCustomLinks = withoutLinks
            .replace(Regex("(?is)<div[^>]*class\\s*=\\s*\\\"link\\\"[^>]*>.*?</div>"), " ")
            .replace(Regex("(?is)<a[^>]*>\\s*(OAAD|Youglish)\\s*</a>"), " ")

        val plain = HtmlCompat.fromHtml(withoutCustomLinks, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
        val normalized = plain
            .replace("\r", "\n")
            .replace(Regex("[ \\t\\f]+"), " ")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
        return ellipsize(normalized, maxChars)
    }

    private fun ellipsize(text: String, max: Int): String {
        if (text.length <= max) return text
        return text.substring(0, max).trimEnd() + "…"
    }

    private fun loadCardImage(context: Context, card: CardInfo): Bitmap? = null

    private fun applyImage(collapsedView: RemoteViews, expandedView: RemoteViews, bitmap: Bitmap?) {
        collapsedView.setViewVisibility(R.id.imageViewCollapsed, View.GONE)
        expandedView.setViewVisibility(R.id.imageViewExpanded, View.GONE)
    }
}

// Helper for widget text sanitization，保留基础样式（粗体/斜体/上下标），列表/引用转换，并移除链接。
fun sanitizeForWidget(raw: String?, fallback: String?): CharSequence {
    val source = when {
        !raw.isNullOrBlank() -> raw
        !fallback.isNullOrBlank() -> fallback
        else -> ""
    }
    val NL = "§§NL§§"
    fun applyPrefixMarkers(html: String): String {
        val prefixTokens = listOf("❯ ", "➤ ", "→ ", "▪ ", "✎ ", "• ")
        val rules = listOf(
            "def" to "❯ ",
            "definition" to "❯ ",
            "mean" to "❯ ",
            "sentence" to "➤ ",
            "quote" to "❯ ",
            "tran" to "→ ",
            "translation" to "→ ",
            "tr" to "→ ",
            "ex-tr" to "→ ",
            "ex" to "▪ ",
            "example" to "▪ ",
            "note" to "✎ ",
            "memo" to "✎ ",
            "zh" to "• ",
            "cn" to "• ",
            "chinese" to "• "
        )
        var updated = html
        for ((keyword, prefix) in rules) {
            val regex = Regex("(?is)<([a-z0-9]+)([^>]*)((class|id|aria-label)\\s*=\\s*\"[^\"]*?${keyword}[^\"]*\")[^>]*>")
            updated = regex.replace(updated) { mr ->
                val start = mr.range.first
                val before = updated.substring(0, start)
                val already = prefixTokens.any { tok ->
                    before.endsWith("$NL$tok") || before.endsWith("\n$tok")
                }
                if (already) {
                    mr.value
                } else {
                    "$NL$prefix${mr.value}"
                }
            }
        }
        return updated
    }
    val withAudioIcon = source
        .replace(Regex("\\[sound:[^\\]]+\\]"), "\uD83D\uDD0A ")
        .replace(Regex("(?is)<audio[^>]*src\\s*=\\s*\\\"([^\\\"]+)\\\"[^>]*>(?:.*?)</audio>"), "\uD83D\uDD0A ")
        .replace(Regex("(?is)<audio[^>]*>"), "\uD83D\uDD0A ")
    val clozeHandled = withAudioIcon.replace(Regex("\\{\\{c(\\d+)::(.*?)(::(.*?))?\\}\\}", RegexOption.IGNORE_CASE)) {
        val idx = it.groupValues.getOrNull(1) ?: ""
        val text = it.groupValues.getOrNull(2) ?: ""
        "[C$idx] $text"
    }
    val listsHandled = clozeHandled
        .replace(Regex("(?is)<ol[^>]*>|<ul[^>]*>"), "\n")
        .replace(Regex("(?is)</ol>|</ul>"), "\n")
        .replace(Regex("(?is)<li[^>]*>"), "\n• ")
        .replace(Regex("(?is)</li>"), "")
    val withQuotes = listsHandled
        // 引用块用特殊前缀区分，结尾增加一个空行保持视觉间距
        .replace(Regex("(?is)<blockquote[^>]*>"), "\n❯ ")
        .replace(Regex("(?is)</blockquote>"), "\n\n")

    val prefixed = applyPrefixMarkers(withQuotes)

    val blockCollapsed = prefixed
        .replace(Regex("(?is)<p[^>]*>"), NL)
        .replace(Regex("(?is)</p>"), NL)
        .replace(Regex("(?is)<div[^>]*>"), NL)
        .replace(Regex("(?is)</div>"), NL)
        .replace(Regex("(?is)<br\\s*/?>"), NL)
        .replace(Regex("(?is)<hr[^>]*>"), NL)
        .replace(Regex("(§§NL§§\\s*){2,}"), NL)

    val cleanedHtml = blockCollapsed
        .replace(Regex("(?is)<img[^>]*>"), " ")
        .replace(Regex("(?is)<a[^>]*>\\s*(.*?)\\s*</a>"), " ")
        .replace(Regex("https?://\\S+"), " ")
        .replace(Regex("\\r"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .replace(Regex("(<br\\s*/?>\\s*){2,}"), "<br/>")
        .trim()

    val plain = HtmlCompat.fromHtml(cleanedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    val withMarkers = plain.replace(NL, "\n")
    val normalized = withMarkers
        .replace("\r", "\n")
        .replace(Regex("[ \\t\\f]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .replace(Regex(" +\\n"), "\n")
        .replace(Regex("\\n +"), "\n")
        .trim()
    if (normalized.isBlank()) return ""

    // 压缩多余的空行，去掉因移除图片/链接而残留的空白行
    val builder = StringBuilder()
    var lastBlank = false
    normalized.lines().forEach { line ->
        val trimmed = line.trim()
        val isBlank = trimmed.isEmpty()
        if (isBlank) {
            if (!lastBlank && builder.isNotEmpty()) {
                builder.append("\n")
            }
            lastBlank = true
        } else {
            if (builder.isNotEmpty()) builder.append("\n")
            builder.append(trimmed)
            lastBlank = false
        }
    }
    val compacted = builder.toString().trim()
    // 前缀兜底换行：如未换行则插入一行，避免段落黏连
    val prefixPattern = Regex("(?m)([^\\n])\\s*(❯|➤|→|▪|✎|•)\\s")
    val ensured = compacted.replace(prefixPattern, "$1\n$2 ")
    return ensured.trim()
}
