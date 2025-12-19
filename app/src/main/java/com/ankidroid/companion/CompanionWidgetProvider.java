package com.ankidroid.companion;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.widget.RemoteViews;
import com.ankidroid.companion.UserPreferences.CardSourceMode;

public class CompanionWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.ankidroid.companion.widget.REFRESH";
    public static final String ACTION_OPEN_APP = "com.ankidroid.companion.widget.OPEN_APP";
    public static final String ACTION_EASE_1 = "com.ankidroid.companion.widget.EASE_1";
    public static final String ACTION_EASE_2 = "com.ankidroid.companion.widget.EASE_2";
    public static final String ACTION_EASE_3 = "com.ankidroid.companion.widget.EASE_3";
    public static final String ACTION_EASE_4 = "com.ankidroid.companion.widget.EASE_4";
    public static final String ACTION_RANDOM_PREV = "com.ankidroid.companion.widget.RANDOM_PREV";
    public static final String ACTION_RANDOM_NEXT = "com.ankidroid.companion.widget.RANDOM_NEXT";
    public static final String ACTION_RANDOM_REFRESH = "com.ankidroid.companion.widget.RANDOM_REFRESH";
    public static final String ACTION_RANDOM_RESERVED = "com.ankidroid.companion.widget.RANDOM_RESERVED";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static long lastToastTimeMs = 0L;
    private static final long TOAST_COOLDOWN_MS = 3000L;
    private static boolean pendingRetry = false;
    private static long pendingNoteId = -1;
    private static int pendingOrd = -1;
    // Random mode cache
    private static final java.util.LinkedList<CardInfo> randomQueue = new java.util.LinkedList<>();
    private static int randomIndex = -1;
    private static boolean randomRoamActive = false;
    private static int lastQueueSize = -1;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            refreshAndUpdate(context);
        } else if (ACTION_OPEN_APP.equals(action)) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.ichi2.anki");
            if (launch == null) {
                launch = new Intent(context, MainActivity.class);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } else if (ACTION_RANDOM_PREV.equals(action) || ACTION_RANDOM_NEXT.equals(action) ||
                ACTION_RANDOM_REFRESH.equals(action) || ACTION_RANDOM_RESERVED.equals(action)) {
            handleRandomAction(context, action);
        } else if (ACTION_EASE_1.equals(action)) {
            respondCard(context, AnkiDroidHelper.EASE_1);
        } else if (ACTION_EASE_2.equals(action)) {
            respondCard(context, AnkiDroidHelper.EASE_2);
        } else if (ACTION_EASE_3.equals(action)) {
            respondCard(context, AnkiDroidHelper.EASE_3);
        } else if (ACTION_EASE_4.equals(action)) {
            respondCard(context, AnkiDroidHelper.EASE_4);
        }
    }

    private void refreshAndUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CompanionWidgetProvider.class));
        if (ids == null || ids.length == 0) return;

        AnkiDroidHelper helper = new AnkiDroidHelper(context);
        StoredState state = helper.getStoredState();
        CardInfo card = null;
        String deckName = "";
        if (state != null && state.deckId > 0 && helper.isPermissionGranted()) {
            try {
                String dn = helper.getApi().getDeckName(state.deckId);
                deckName = dn != null ? dn : "";
                CardSourceMode mode = UserPreferences.INSTANCE.getCardSourceMode(context);
                if (mode == CardSourceMode.REVIEW) {
            card = helper.getTopCardForDeck(state.deckId);
            randomQueue.clear();
            randomIndex = -1;
            randomRoamActive = false;
            lastQueueSize = -1;
        } else {
            buildRandomCache(context, helper, state.deckId, mode);
            if (randomIndex >= 0 && randomIndex < randomQueue.size()) {
                card = randomQueue.get(randomIndex);
            }
                }
                if (card != null) helper.storeState(state.deckId, card);
            } catch (Exception ignored) {
                card = null;
                deckName = "";
            }
        } else {
            android.util.Log.w("CompanionWidget", "refreshAndUpdate: missing state or permission. state=" + state);
        }
        for (int id : ids) {
            updateWidget(context, manager, id, card);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int appWidgetId, CardInfo card) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_companion);

        AnkiDroidHelper helper = new AnkiDroidHelper(context);
        StoredState state = helper.getStoredState();
        String deckName = "";
        if (state != null && state.deckId > 0 && helper.isPermissionGranted()) {
            try {
                String dn = helper.getApi().getDeckName(state.deckId);
                deckName = dn != null ? dn : "";
            } catch (Exception ignored) {
                deckName = "";
            }
        }
        FieldMode mode = UserPreferences.INSTANCE.getFieldMode(context);
        CardSourceMode widgetMode = UserPreferences.INSTANCE.getCardSourceMode(context);

        // Choose short labels on narrow widgets to avoid inflating button height
        boolean useShortLabels = false;
        try {
            Bundle opts = manager.getAppWidgetOptions(appWidgetId);
            int minWidth = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
            // Only shorten labels on very narrow widths to avoid misclassifying wider layouts
            useShortLabels = minWidth > 0 && minWidth < 120; // treat <120dp as narrow
        } catch (Exception ignored) {
        }
        if (useShortLabels) {
            views.setTextViewText(R.id.widgetButtonAgain, "Aga");
            views.setTextViewText(R.id.widgetButtonHard, "Har");
            views.setTextViewText(R.id.widgetButtonGood, "Goo");
            views.setTextViewText(R.id.widgetButtonEasy, "Eas");
        } else {
            views.setTextViewText(R.id.widgetButtonAgain, context.getString(R.string.again));
            views.setTextViewText(R.id.widgetButtonHard, context.getString(R.string.hard));
            views.setTextViewText(R.id.widgetButtonGood, context.getString(R.string.good));
            views.setTextViewText(R.id.widgetButtonEasy, context.getString(R.string.easy));
        }
        // Force single-line buttons on narrow widgets to prevent wrapping/stacking
        views.setInt(R.id.widgetButtonAgain, "setMaxLines", 1);
        views.setInt(R.id.widgetButtonHard, "setMaxLines", 1);
        views.setInt(R.id.widgetButtonGood, "setMaxLines", 1);
        views.setInt(R.id.widgetButtonEasy, "setMaxLines", 1);
        views.setBoolean(R.id.widgetButtonAgain, "setSingleLine", true);
        views.setBoolean(R.id.widgetButtonHard, "setSingleLine", true);
        views.setBoolean(R.id.widgetButtonGood, "setSingleLine", true);
        views.setBoolean(R.id.widgetButtonEasy, "setSingleLine", true);

        if (card == null && state != null && state.cardOrd != -1 && helper.isPermissionGranted()) {
            if (widgetMode == CardSourceMode.REVIEW) {
                card = helper.queryCurrentScheduledCard(state.deckId, CardSourceMode.REVIEW);
            } else {
                buildRandomCache(context, helper, state.deckId, widgetMode);
                if (randomIndex >= 0 && randomIndex < randomQueue.size()) {
                    card = randomQueue.get(randomIndex);
                }
            }
        }

        if (card != null) {
            CharSequence front = NotificationsKt.sanitizeForWidget(card.rawQuestion, card.simpleQuestion);
            CharSequence back = NotificationsKt.sanitizeForWidget(card.rawAnswer, card.simpleAnswer);
            android.util.Log.d("CompanionWidget",
                    "render card deckId=" + (state != null ? state.deckId : -1)
                            + " note=" + card.noteID + " ord=" + card.cardOrd + " model=" + card.modelId
                            + " mode=" + widgetMode
                            + " front=\"" + truncate(front, 160) + "\" back=\"" + truncate(back, 160) + "\"");
            views.setTextViewText(R.id.widgetTitle, deckName.isEmpty() ? context.getString(R.string.app_name) : deckName);
            int estimatedLines = estimateLines(front) + estimateLines(back);
            boolean showButtons = estimatedLines >= 3;
            if (mode == FieldMode.QUESTION_ONLY) {
                views.setViewVisibility(R.id.widgetFront, android.view.View.VISIBLE);
                int maxLines = showButtons ? 30 : 200;
                views.setInt(R.id.widgetFront, "setMaxLines", maxLines);
                views.setTextViewText(R.id.widgetFront, front);
                views.setViewVisibility(R.id.widgetBack, android.view.View.GONE);
            } else if (mode == FieldMode.ANSWER_ONLY) {
                views.setViewVisibility(R.id.widgetFront, android.view.View.GONE);
                views.setViewVisibility(R.id.widgetBack, android.view.View.VISIBLE);
                int maxLines = showButtons ? 30 : 200;
                views.setInt(R.id.widgetBack, "setMaxLines", maxLines);
                views.setTextViewText(R.id.widgetBack, back);
            } else {
                views.setViewVisibility(R.id.widgetFront, android.view.View.VISIBLE);
                views.setViewVisibility(R.id.widgetBack, android.view.View.VISIBLE);
                int allowedTotal = showButtons ? 40 : 200;
                int frontEst = Math.max(1, estimateLines(front));
                int backEst = Math.max(1, estimateLines(back));
                int totalEst = frontEst + backEst;
                int frontLines = Math.max(2, Math.min(allowedTotal - 2, allowedTotal * frontEst / totalEst));
                int backLines = Math.max(2, allowedTotal - frontLines);
                views.setInt(R.id.widgetFront, "setMaxLines", frontLines);
                views.setInt(R.id.widgetBack, "setMaxLines", backLines);
                views.setTextViewText(R.id.widgetFront, front);
                views.setTextViewText(R.id.widgetBack, back);
            }
            views.setViewVisibility(R.id.widgetButtons, showButtons ? android.view.View.VISIBLE : android.view.View.GONE);
            boolean showModeLabel = true;
            if (widgetMode == CardSourceMode.REVIEW) {
                views.setTextViewText(R.id.widgetRoamLabel, context.getString(R.string.widget_roam_label_review));
                randomRoamActive = false;
            } else if (widgetMode == CardSourceMode.RANDOM_QUEUE) {
                if (randomRoamActive && lastQueueSize >= 0) {
                    views.setTextViewText(R.id.widgetRoamLabel, context.getString(R.string.widget_roam_label_fallback, lastQueueSize));
                } else {
                    views.setTextViewText(R.id.widgetRoamLabel, context.getString(R.string.widget_roam_label_random_queue));
                }
            } else if (widgetMode == CardSourceMode.RANDOM_ROAM) {
                views.setTextViewText(R.id.widgetRoamLabel, context.getString(R.string.widget_roam_label_random_roam));
            } else {
                showModeLabel = false;
            }
            views.setViewVisibility(R.id.widgetRoamLabel, showModeLabel ? android.view.View.VISIBLE : android.view.View.GONE);
            if (widgetMode == CardSourceMode.REVIEW) {
                views.setViewVisibility(R.id.widgetButtonAgain, android.view.View.VISIBLE);
                views.setViewVisibility(R.id.widgetButtonHard, android.view.View.VISIBLE);
                views.setViewVisibility(R.id.widgetButtonGood, android.view.View.VISIBLE);
                views.setViewVisibility(R.id.widgetButtonEasy, android.view.View.VISIBLE);
                views.setOnClickPendingIntent(R.id.widgetButtonAgain, getPendingIntent(context, ACTION_EASE_1));
                views.setOnClickPendingIntent(R.id.widgetButtonHard, getPendingIntent(context, ACTION_EASE_2));
                views.setOnClickPendingIntent(R.id.widgetButtonGood, getPendingIntent(context, ACTION_EASE_3));
                views.setOnClickPendingIntent(R.id.widgetButtonEasy, getPendingIntent(context, ACTION_EASE_4));
            } else {
                // Only Prev/Next for random mode; hide other buttons to save space.
                views.setTextViewText(R.id.widgetButtonAgain, "Prev");
                views.setTextViewText(R.id.widgetButtonHard, "Next");
                views.setViewVisibility(R.id.widgetButtonGood, android.view.View.GONE);
                views.setViewVisibility(R.id.widgetButtonEasy, android.view.View.GONE);
                views.setOnClickPendingIntent(R.id.widgetButtonAgain, getPendingIntent(context, ACTION_RANDOM_PREV));
                views.setOnClickPendingIntent(R.id.widgetButtonHard, getPendingIntent(context, ACTION_RANDOM_NEXT));
            }
        } else {
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.app_name));
            views.setTextViewText(R.id.widgetFront, context.getString(R.string.widget_empty_prompt));
            views.setViewVisibility(R.id.widgetFront, android.view.View.VISIBLE);
            views.setViewVisibility(R.id.widgetBack, android.view.View.GONE);
            views.setViewVisibility(R.id.widgetButtons, android.view.View.GONE);
        }

        views.setOnClickPendingIntent(R.id.widgetRefresh, getPendingIntent(context, ACTION_REFRESH));
        views.setOnClickPendingIntent(R.id.widgetCardArea, getPendingIntent(context, ACTION_OPEN_APP));

        manager.updateAppWidget(appWidgetId, views);

        if (card == null) {
            android.util.Log.w("CompanionWidget", "updateWidget: no card available. deckName=" + deckName + " state=" + state + " mode=" + widgetMode + " randomQueueSize=" + randomQueue.size());
        }
    }

    private PendingIntent getPendingIntent(Context context, String action) {
        Intent intent = new Intent(context, CompanionWidgetProvider.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private int estimateLines(CharSequence text) {
        if (text == null) return 0;
        String s = text.toString();
        int newlines = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') newlines++;
        }
        int approx = s.length() / 25;
        return Math.max(newlines + 1, approx + 1);
    }

    private void showToast(Context context, String msg) {
        long now = System.currentTimeMillis();
        if (now - lastToastTimeMs < TOAST_COOLDOWN_MS) {
            return;
        }
        lastToastTimeMs = now;
        handler.post(() ->
                android.widget.Toast.makeText(context.getApplicationContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
        );
    }

    private void handleRandomAction(Context context, String action) {
        AnkiDroidHelper helper = new AnkiDroidHelper(context);
        StoredState state = helper.getStoredState();
        if (state == null || state.deckId == -1) {
            refreshAndUpdate(context);
            return;
        }
        CardSourceMode mode = UserPreferences.INSTANCE.getCardSourceMode(context);
        buildRandomCache(context, helper, state.deckId, mode);
        switch (action) {
            case ACTION_RANDOM_PREV:
                if (randomIndex > 0) randomIndex--;
                break;
            case ACTION_RANDOM_NEXT:
                if (randomIndex < randomQueue.size() - 1) {
                    randomIndex++;
                } else {
                    // If at end, fetch another random and append
                    CardInfo more = helper.queryCurrentScheduledCard(state.deckId, CardSourceMode.RANDOM_QUEUE);
                    if (more != null) {
                        randomQueue.add(more);
                        randomIndex = randomQueue.size() - 1;
                    }
                }
                break;
            case ACTION_RANDOM_REFRESH:
            case ACTION_REFRESH:
                buildRandomCache(context, helper, state.deckId, mode);
                break;
            case ACTION_RANDOM_RESERVED:
                // Hold: do nothing, just stay on current
                break;
        }
        if (randomIndex >= 0 && randomIndex < randomQueue.size()) {
            CardInfo current = randomQueue.get(randomIndex);
            helper.storeState(state.deckId, current);
        }
        refreshAndUpdate(context);
    }

    private String truncate(CharSequence text, int max) {
        if (text == null) return "";
        String s = text.toString();
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    private void buildRandomCache(Context context, AnkiDroidHelper helper, long deckId, CardSourceMode mode) {
        int target = UserPreferences.INSTANCE.getRandomCacheSize(context);
        int threshold = UserPreferences.INSTANCE.getRandomQueueThreshold(context);
        int sampleLimit = UserPreferences.INSTANCE.getRandomSampleLimit(context);
        randomQueue.clear();
        randomIndex = -1;
        randomRoamActive = false;
        lastQueueSize = -1;

        java.util.List<CardInfo> source = new java.util.ArrayList<>();
        if (mode == CardSourceMode.RANDOM_QUEUE) {
            java.util.List<CardInfo> queueCards = helper.fetchQueueCards(deckId, true);
            lastQueueSize = queueCards.size();
            if (queueCards.size() >= threshold) {
                source = queueCards;
            } else {
                // Fallback to deck-random to keep the widget populated.
                source = helper.fetchRandomDeckCards(deckId, target, sampleLimit);
                randomRoamActive = true;
            }
        } else if (mode == CardSourceMode.RANDOM_ROAM) {
            source = helper.fetchRandomDeckCardsNoReview(deckId, target, sampleLimit);
            randomRoamActive = true;
            lastQueueSize = -1;
        }
        // Fallback: if random source is empty (e.g., card provider unsupported), try a queue card to avoid blank widget.
        if (source == null || source.isEmpty()) {
            CardInfo fallback = helper.queryCurrentScheduledCard(deckId, CardSourceMode.REVIEW);
            if (fallback != null) {
                source = new java.util.ArrayList<>();
                source.add(fallback);
                randomRoamActive = false;
            }
        }
        if (source != null) {
            if (source.size() > target) {
                java.util.Collections.shuffle(source);
                source = source.subList(0, target);
            }
            randomQueue.addAll(source);
        }
        if (!randomQueue.isEmpty()) {
            randomIndex = 0;
        }
        android.util.Log.d("CompanionWidget", "buildRandomCache: mode=" + mode + " deckId=" + deckId + " sourceSize=" + (source == null ? -1 : source.size()) + " queueSize=" + randomQueue.size() + " threshold=" + threshold + " randomRoamActive=" + randomRoamActive);
    }

    private void respondCard(Context context, int ease) {
        AnkiDroidHelper helper = new AnkiDroidHelper(context);
        StoredState state = helper.getStoredState();
        if (state == null || state.cardOrd == -1) {
            android.util.Log.w("CompanionWidget", "No stored state; refreshing.");
            refreshAndUpdate(context);
            return;
        }
        CardSourceMode mode = UserPreferences.INSTANCE.getCardSourceMode(context);
        if (mode != CardSourceMode.REVIEW) {
            // In random mode, feedback buttons repurposed; keep just cycling random cards.
            handleRandomAction(context, ACTION_RANDOM_REFRESH);
            return;
        }
        // If a retry was requested, only proceed when state matches pending and top matches too.
        if (pendingRetry) {
            CardInfo topRetry = helper.getTopCardForDeck(state.deckId);
            if (topRetry == null || topRetry.noteID != pendingNoteId || topRetry.cardOrd != pendingOrd) {
                android.util.Log.w("CompanionWidget", "Pending retry but top/state mismatch; refreshing and asking again.");
                if (topRetry != null) {
                    helper.storeState(state.deckId, topRetry);
                    pendingNoteId = topRetry.noteID;
                    pendingOrd = topRetry.cardOrd;
                }
                showToast(context, "Card updated, tap again to answer");
                refreshAndUpdate(context);
                return;
            }
        }
        // Sync with the top of the queue to avoid "not at top of queue" errors.
        CardInfo top = helper.getTopCardForDeck(state.deckId);
        if (top != null && (top.noteID != state.noteID || top.cardOrd != state.cardOrd)) {
            android.util.Log.w("CompanionWidget", "Top card mismatch, updating state. top noteId=" + top.noteID + " ord=" + top.cardOrd + " stored noteId=" + state.noteID + " ord=" + state.cardOrd);
            helper.storeState(state.deckId, top);
            showToast(context, "Card updated, tap again to answer");
            pendingRetry = true;
            pendingNoteId = top.noteID;
            pendingOrd = top.cardOrd;
            refreshAndUpdate(context);
            return;
        }

        boolean ok = helper.reviewCard(state.noteID, state.cardOrd, state.cardStartTime, ease);
        if (!ok) {
            // On failure (card changed), show a lightweight toast and refresh widget after a short delay.
            android.util.Log.w("CompanionWidget", "Review failed for noteId=" + state.noteID + " ord=" + state.cardOrd + ", will refresh.");
            showToast(context, "Card changed, refreshing…");
            pendingRetry = false;
            CardInfo fallback = helper.queryCurrentScheduledCard(state.deckId);
            if (fallback != null) {
                helper.storeState(state.deckId, fallback);
                android.util.Log.w("CompanionWidget", "Stored fallback card noteId=" + fallback.noteID + " ord=" + fallback.cardOrd);
            } else {
                CardInfo empty = new CardInfo();
                empty.cardOrd = -1;
                empty.noteID = -1;
                helper.storeState(state.deckId, empty);
            }
            // Immediate refresh plus delayed refresh as a safeguard.
            refreshAndUpdate(context);
            handler.postDelayed(() -> refreshAndUpdate(context), 2000);
            return;
        }
        // After a successful review, fetch next card. If AnkiDroid hasn't advanced yet, retry shortly.
        pendingRetry = false;
        CardInfo next = helper.queryCurrentScheduledCard(state.deckId);
        if (next != null && (next.noteID != state.noteID || next.cardOrd != state.cardOrd)) {
            android.util.Log.i("CompanionWidget", "Next card immediately available noteId=" + next.noteID + " ord=" + next.cardOrd);
            helper.storeState(state.deckId, next);
            refreshAndUpdate(context);
        } else {
            android.util.Log.i("CompanionWidget", "Next card same or null immediately; retrying after delay. current noteId=" + state.noteID + " ord=" + state.cardOrd);
            handler.postDelayed(() -> {
                CardInfo later = helper.queryCurrentScheduledCard(state.deckId);
                if (later != null && (later.noteID != state.noteID || later.cardOrd != state.cardOrd)) {
                    android.util.Log.i("CompanionWidget", "Next card after delay noteId=" + later.noteID + " ord=" + later.cardOrd);
                    helper.storeState(state.deckId, later);
                    refreshAndUpdate(context);
                } else {
                    android.util.Log.w("CompanionWidget", "Still same card after delay; asking user to tap again.");
                    pendingRetry = true;
                    pendingNoteId = state.noteID;
                    pendingOrd = state.cardOrd;
                    showToast(context, "Card unchanged, tap again");
                    refreshAndUpdate(context);
                }
            }, 300);
        }
    }
}
