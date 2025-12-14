package com.ankidroid.companion;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

public class CompanionWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.ankidroid.companion.widget.REFRESH";
    public static final String ACTION_OPEN_APP = "com.ankidroid.companion.widget.OPEN_APP";
    public static final String ACTION_EASE_1 = "com.ankidroid.companion.widget.EASE_1";
    public static final String ACTION_EASE_2 = "com.ankidroid.companion.widget.EASE_2";
    public static final String ACTION_EASE_3 = "com.ankidroid.companion.widget.EASE_3";
    public static final String ACTION_EASE_4 = "com.ankidroid.companion.widget.EASE_4";

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

        if (state != null) {
            deckName = helper.getApi().getDeckName(state.deckId);
            card = helper.queryCurrentScheduledCard(state.deckId);
            if (card != null) {
                helper.storeState(state.deckId, card);
            }
        }

        for (int id : ids) {
            updateWidget(context, manager, id, card);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int appWidgetId, CardInfo card) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_companion);

        AnkiDroidHelper helper = new AnkiDroidHelper(context);
        StoredState state = helper.getStoredState();
        String deckName = state != null ? helper.getApi().getDeckName(state.deckId) : "";
        FieldMode mode = UserPreferences.INSTANCE.getFieldMode(context);

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

        if (card == null && state != null && state.cardOrd != -1) {
            card = helper.queryCurrentScheduledCard(state.deckId);
        }

        if (card != null) {
            CharSequence front = NotificationsKt.sanitizeForWidget(card.rawQuestion, card.simpleQuestion);
            CharSequence back = NotificationsKt.sanitizeForWidget(card.rawAnswer, card.simpleAnswer);
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
            views.setOnClickPendingIntent(R.id.widgetButtonAgain, getPendingIntent(context, ACTION_EASE_1));
            views.setOnClickPendingIntent(R.id.widgetButtonHard, getPendingIntent(context, ACTION_EASE_2));
            views.setOnClickPendingIntent(R.id.widgetButtonGood, getPendingIntent(context, ACTION_EASE_3));
            views.setOnClickPendingIntent(R.id.widgetButtonEasy, getPendingIntent(context, ACTION_EASE_4));
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

    private void respondCard(Context context, int ease) {
        AnkiDroidHelper helper = new AnkiDroidHelper(context);
        StoredState state = helper.getStoredState();
        if (state == null || state.cardOrd == -1) {
            refreshAndUpdate(context);
            return;
        }
        helper.reviewCard(state.noteID, state.cardOrd, state.cardStartTime, ease);
        CardInfo next = helper.queryCurrentScheduledCard(state.deckId);
        if (next != null) {
            helper.storeState(state.deckId, next);
        } else {
            CardInfo empty = new CardInfo();
            empty.cardOrd = -1;
            empty.noteID = -1;
            helper.storeState(state.deckId, empty);
        }
        refreshAndUpdate(context);
    }
}
