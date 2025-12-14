package com.ankidroid.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("Notifications", "onReceive called")
        if (context == null)
            return
        Log.i("Notifications", "onReceive called - context is not null")
        when (intent?.action) {
            "ACTION_BUTTON_1" -> respondCard(context, AnkiDroidHelper.EASE_1)
            "ACTION_BUTTON_2" -> respondCard(context, AnkiDroidHelper.EASE_2)
            "ACTION_BUTTON_3" -> respondCard(context, AnkiDroidHelper.EASE_3)
            "ACTION_BUTTON_4" -> respondCard(context, AnkiDroidHelper.EASE_4)
        }
    }

    private fun respondCard(context: Context, ease: Int) {
        Log.i("Notifications", "respondCard called")
        var mAnkiDroid = AnkiDroidHelper(context)
        val localState = mAnkiDroid.storedState

        if (localState == null) {
            Log.w("Notifications", "No stored state found when responding to card.")
            return
        }

        // Sync with top of queue; if different, update state and ask user to tap again.
        val top = mAnkiDroid.getTopCardForDeck(localState.deckId)
        if (top != null && (top.noteID != localState.noteID || top.cardOrd != localState.cardOrd)) {
            mAnkiDroid.storeState(localState.deckId, top)
            Log.w("Notifications", "Card updated at top of queue; user should tap again.")
            if (UserPreferences.getNotificationsEnabled(context)) {
                Notifications().showNotification(context, top, mAnkiDroid.currentDeckName, true)
            }
            return
        }

        Log.i("Notifications", "localState.cardOrd: ${localState.cardOrd}, localState.noteID: ${localState.noteID}")
        val ok = mAnkiDroid.reviewCard(localState.noteID, localState.cardOrd, localState.cardStartTime, ease)
        if (!ok) {
            Log.w("Notifications", "Review failed (card changed); refreshing state.")
            val refreshed = mAnkiDroid.queryCurrentScheduledCard(localState.deckId)
            if (refreshed != null) {
                mAnkiDroid.storeState(localState.deckId, refreshed)
                if (UserPreferences.getNotificationsEnabled(context)) {
                    Notifications.create().showNotification(context, refreshed, mAnkiDroid.currentDeckName, true)
                }
            }
            return
        }

        // Move to next card
        val nextCard = mAnkiDroid.queryCurrentScheduledCard(localState.deckId)
        if (nextCard != null) {
            Log.i("Notifications", "moving to next card.")
            mAnkiDroid.storeState(localState.deckId, nextCard)
            Notifications.create().showNotification(context, nextCard, mAnkiDroid.currentDeckName, true)
        } else {
            Log.i("Notifications", "no other cards found, showing done notification")
            // No more cards to show.
            val emptyCard = CardInfo()
            emptyCard.cardOrd = -1
            emptyCard.noteID = -1
            mAnkiDroid.storeState(localState.deckId, emptyCard)
            Notifications.create().showNotification(context, null, "", true)
        }
    }
}
