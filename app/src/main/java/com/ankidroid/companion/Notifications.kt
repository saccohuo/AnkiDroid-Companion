package com.ankidroid.companion

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import com.ichi2.anki.api.AddContentApi
import java.io.File


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

            val (headerText, contentText) = when (fieldMode) {
                FieldMode.BOTH -> Pair(questionCollapsed, answerCollapsed)
                FieldMode.QUESTION_ONLY -> Pair(questionCollapsed, "")
                FieldMode.ANSWER_ONLY -> Pair("", answerCollapsed)
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

    private fun sanitizeText(raw: String?, fallback: String?, maxChars: Int): String {
        val source = when {
            !raw.isNullOrBlank() -> raw
            !fallback.isNullOrBlank() -> fallback
            else -> ""
        }
        val withoutSound = source.replace(Regex("\\[sound:[^\\]]+\\]"), "")
        val withoutImages = withoutSound.replace(Regex("(?is)<img[^>]*>"), " ")
        val withoutUrls = withoutImages.replace(Regex("https?://\\S+"), "")
        val withoutCustomLinks = withoutUrls
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

    private fun loadCardImage(context: Context, card: CardInfo): Bitmap? {
        val names = card.fileNames ?: return null
        val candidates = mutableListOf<String>()
        for (i in 0 until names.length()) {
            val name = names.optString(i)
            if (isImageFile(name)) {
                candidates.add(name)
            }
        }
        // Also parse html for img src filenames.
        candidates.addAll(extractImageSources(card.rawQuestion))
        candidates.addAll(extractImageSources(card.rawAnswer))

        for (name in candidates) {
            resolveMediaFile(context, name)?.let { file ->
                loadBitmap(file)?.let { return it }
            }
        }
        return null
    }

    private fun isImageFile(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
    }

    private fun resolveMediaFile(context: Context, name: String): File? {
        // Try AddContentApi.getMediaFile via reflection for compatibility.
        try {
            val api = AddContentApi(context)
            val method = AddContentApi::class.java.getMethod("getMediaFile", String::class.java)
            val file = method.invoke(api, name)
            if (file is File && file.exists()) {
                return file
            }
        } catch (e: Exception) {
            Log.w("Notifications", "Media resolver via API failed: ${e.message}")
        }

        // Fallback: common AnkiDroid media folder
        return try {
            val mediaDir = File(android.os.Environment.getExternalStorageDirectory(),
                "Android/data/com.ichi2.anki/files/AnkiDroid/collection.media")
            val candidate = File(mediaDir, name)
            if (candidate.exists()) candidate else null
        } catch (e: Exception) {
            Log.w("Notifications", "Media resolver via path failed: ${e.message}")
            null
        }
    }

    private fun extractImageSources(html: String?): List<String> {
        if (html.isNullOrBlank()) return emptyList()
        val regex = Regex("(?is)<img[^>]*src\\s*=\\s*\\\"([^\\\"]+)\\\"")
        return regex.findAll(html).mapNotNull { match ->
            match.groupValues.getOrNull(1)?.substringAfterLast('/')
        }.filter { isImageFile(it) }.toList()
    }

    private fun loadBitmap(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.w("Notifications", "Bitmap decode failed: ${e.message}")
            null
        }
    }

    private fun applyImage(collapsedView: RemoteViews, expandedView: RemoteViews, bitmap: Bitmap?) {
        if (bitmap != null) {
            collapsedView.setViewVisibility(R.id.imageViewCollapsed, View.VISIBLE)
            collapsedView.setImageViewBitmap(R.id.imageViewCollapsed, bitmap)
            expandedView.setViewVisibility(R.id.imageViewExpanded, View.VISIBLE)
            expandedView.setImageViewBitmap(R.id.imageViewExpanded, bitmap)
        } else {
            collapsedView.setViewVisibility(R.id.imageViewCollapsed, View.GONE)
            expandedView.setViewVisibility(R.id.imageViewExpanded, View.GONE)
        }
    }
}
