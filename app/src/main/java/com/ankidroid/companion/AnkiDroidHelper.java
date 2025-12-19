package com.ankidroid.companion;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.api.AddContentApi;
import static com.ichi2.anki.api.AddContentApi.READ_WRITE_PERMISSION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AnkiDroidHelper {
    public static final int EASE_1 = 1;
    public static final int EASE_2 = 2;
    public static final int EASE_3 = 3;
    public static final int EASE_4 = 4;

    public static final String[] CARD_PROJECTION = {
            FlashCardsContract.Card.ANSWER,
            FlashCardsContract.Card.QUESTION,
            FlashCardsContract.Card.ANSWER_PURE,
            FlashCardsContract.Card.QUESTION_SIMPLE};
    private static final String DECK_REF_DB = "com.ichi2.anki.api.decks";
    private static final String STATE_DB = "com.ichi2.anki.api.state";

    private static final String KEY_CURRENT_STATE = "CURRENT_STATE";

    private AddContentApi mApi;
    private Context mContext;
    private final Handler uiHandler = new Handler();

    public AnkiDroidHelper(Context context) {
        Log.i("BackgroundService", "AnkiDroidHelper constructor - 1");
        mContext = context.getApplicationContext();
        Log.i("BackgroundService", "AnkiDroidHelper constructor - 2");
        mApi = new AddContentApi(mContext);
        Log.i("BackgroundService", "AnkiDroidHelper constructor - 3");
    }

    public AddContentApi getApi() {
        return mApi;
    }

    /**
     * Whether or not the API is available to use.
     * The API could be unavailable if AnkiDroid is not installed or the user explicitly disabled the API
     * @return true if the API is available to use
     */
    public static boolean isApiAvailable(Context context) {
        return AddContentApi.getAnkiDroidPackageName(context) != null;
    }

    /**
     * Whether or not we should request full access to the AnkiDroid API
     */
    public boolean shouldRequestPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        return ContextCompat.checkSelfPermission(mContext, READ_WRITE_PERMISSION) != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request permission from the user to access the AnkiDroid API (for SDK 23+)
     * @param callbackActivity An Activity which implements onRequestPermissionsResult()
     * @param callbackCode The callback code to be used in onRequestPermissionsResult()
     */
    public void requestPermission(Activity callbackActivity, int callbackCode) {
        ActivityCompat.requestPermissions(callbackActivity, new String[]{READ_WRITE_PERMISSION}, callbackCode);
    }

    public boolean isPermissionGranted() {
        return ContextCompat.checkSelfPermission(mContext, READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Save a mapping from deckName to getDeckId in the SharedPreferences
     */
    public void storeDeckReference(String deckName, long deckId) {
        final SharedPreferences decksDb = mContext.getSharedPreferences(DECK_REF_DB, Context.MODE_PRIVATE);
        decksDb.edit().putLong(deckName, deckId).apply();
    }

    /**
     * Try to find the given deck by name, accounting for potential renaming of the deck by the user as follows:
     * If there's a deck with deckName then return it's ID
     * If there's no deck with deckName, but a ref to deckName is stored in SharedPreferences, and that deck exist in
     * AnkiDroid (i.e. it was renamed), then use that deck.Note: this deck will not be found if your app is re-installed
     * If there's no reference to deckName anywhere then return null
     * @param deckName the name of the deck to find
     * @return the did of the deck in Anki
     */
    public Long findDeckIdByName(String deckName) {
        SharedPreferences decksDb = mContext.getSharedPreferences(DECK_REF_DB, Context.MODE_PRIVATE);
        // Look for deckName in the deck list
        Long did = getDeckId(deckName);
        if (did != null) {
            // If the deck was found then return it's id
            return did;
        } else {
            // Otherwise try to check if we have a reference to a deck that was renamed and return that
            did = decksDb.getLong(deckName, -1);
            if (did != -1 && mApi.getDeckName(did) != null) {
                return did;
            } else {
                // If the deck really doesn't exist then return null
                return null;
            }
        }
    }

    public String getCurrentDeckName() {
        StoredState state = getStoredState();
        return mApi.getDeckName(state.deckId);
    }

    /**
     * Get the ID of the deck which matches the name
     * @param deckName Exact name of deck (note: deck names are unique in Anki)
     * @return the ID of the deck that has given name, or null if no deck was found or API error
     */
    private Long getDeckId(String deckName) {
        Map<Long, String> deckList = mApi.getDeckList();
        if (deckList != null) {
            for (Map.Entry<Long, String> entry : deckList.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(deckName)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public void storeState(long deckId, CardInfo card) {
        final SharedPreferences cardsDb = mContext.getSharedPreferences(STATE_DB, Context.MODE_PRIVATE);

        Map<String, Object> message = new HashMap<>();
        message.put("deck_id", deckId);
        if (card != null) {
            message.put("note_id", card.noteID);
            message.put("card_ord", card.cardOrd);
            message.put("start_time", card.cardStartTime);
        } else {
            // Only deck was updated; clear card-related fields.
            message.put("note_id", -1);
            message.put("card_ord", -1);
            message.put("start_time", 0);
        }
        JSONObject js = new JSONObject(message);

        cardsDb.edit().putString(KEY_CURRENT_STATE, js.toString()).apply();
    }

    public StoredState getStoredState() {
        final SharedPreferences cardsDb = mContext.getSharedPreferences(STATE_DB, Context.MODE_PRIVATE);
        String message = cardsDb.getString(KEY_CURRENT_STATE, "");

        // No state found in local
        if (message == "") {
            return null;
        }

        JSONObject json;
        try {
            json = new JSONObject(message);
            StoredState state = new StoredState();
            state.deckId = json.getLong("deck_id");
            state.noteID = json.getLong("note_id");
            state.cardOrd = json.getInt("card_ord");
            state.cardStartTime = json.getLong("start_time");
            return state;
        } catch (JSONException e) {
            // Log.e(TAG, "JSONException " + e);
            e.printStackTrace();
        }
        return null;
    }

    @SuppressLint("Range,DirectSystemCurrentTimeMillisUsage")
    public CardInfo queryCurrentScheduledCard(long deckID) {
        return queryCurrentScheduledCard(deckID, UserPreferences.CardSourceMode.REVIEW);
    }

    public CardInfo queryCurrentScheduledCard(long deckID, UserPreferences.CardSourceMode mode) {
        // Log.d(TAG, "QueryForCurrentCard");
        String[] deckArguments = new String[deckID == -1 ? 1 : 2];
        String deckSelector = "limit=?";
        deckArguments[0] = "" + 20;
        if (deckID != -1) {
            deckSelector += ",deckID=?";
            deckArguments[1] = "" + deckID;
        }

        // This call requires com.ichi2.anki.permission.READ_WRITE_DATABASE to be granted by user as it is
        // marked as "dangerous" by ankidroid app. This permission has been asked before. Would crash if
        // not granted, so checking
        if (!isPermissionGranted()) {
            uiHandler.post(() -> Toast.makeText(mContext,
                    R.string.permission_not_granted,
                    Toast.LENGTH_SHORT).show());
        } else {
            // permission has been granted, normal case

            Cursor reviewInfoCursor =
                    mContext.getContentResolver().query(FlashCardsContract.ReviewInfo.CONTENT_URI, null, deckSelector, deckArguments, null);

            if (reviewInfoCursor == null || !reviewInfoCursor.moveToFirst()) {
                // Log.d(TAG, "query for due card info returned no result");
                if (reviewInfoCursor != null) {
                    reviewInfoCursor.close();
                }
            } else {
                ArrayList<CardInfo> cards = new ArrayList<>();

                // Walk through the cursor to get the responses.
                do {
                    CardInfo card = new CardInfo();

                    card.cardOrd = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD));
                    card.noteID = reviewInfoCursor.getLong(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID));
                    card.buttonCount = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.BUTTON_COUNT));

                    try {
                        card.fileNames = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.MEDIA_FILES)));
                        card.nextReviewTexts = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES)));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    card.cardStartTime = System.currentTimeMillis();
                    // Log.v(TAG, "card added to queue: " + card.fileNames);
                    cards.add(card);
                } while (reviewInfoCursor.moveToNext());

                reviewInfoCursor.close();

                if (cards.size() >= 1) {
                    // Apply template filter if user specified.
                    ArrayList<CardInfo> filtered = new ArrayList<>();
                    final Set<TemplateKey> allowedTemplates = UserPreferences.INSTANCE.getTemplateFilter(mContext);
                    for (CardInfo card : cards) {
                        // fetch modelId for filter & enrichment
                        long modelId = fetchModelId(card.noteID);
                        card.modelId = modelId;

                        if (allowedTemplates.isEmpty() || templateMatches(allowedTemplates, new TemplateKey(card.modelId, card.cardOrd))) {
                            filtered.add(card);
                        }
                    }

                    if (filtered.size() == 0) {
                        // No card matches filter.
                        return null;
                    }

                    cards = filtered;

                    for (CardInfo card : cards) {
                        Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(card.noteID));
                        Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
                        Uri specificCardUri = Uri.withAppendedPath(cardsUri, Integer.toString(card.cardOrd));
                        final Cursor specificCardCursor = mContext.getContentResolver().query(specificCardUri,
                                CARD_PROJECTION,  // projection
                                null,  // selection is ignored for this URI
                                null,  // selectionArgs is ignored for this URI
                                null   // sortOrder is ignored for this URI
                        );

                        if (specificCardCursor == null || !specificCardCursor.moveToFirst()) {
                            // Log.d(TAG, "query for due card info returned no result");
                            if (specificCardCursor != null) {
                                specificCardCursor.close();
                            }
                            return null;
                        } else {
                            card.rawAnswer = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.ANSWER));
                            card.rawQuestion = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.QUESTION));
                            card.simpleAnswer = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.ANSWER_PURE));
                            card.simpleQuestion = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.QUESTION_SIMPLE));
                            card.a = card.rawAnswer != null && !card.rawAnswer.isEmpty() ? card.rawAnswer : card.simpleAnswer;
                            card.q = card.rawQuestion != null && !card.rawQuestion.isEmpty() ? card.rawQuestion : card.simpleQuestion;
                            specificCardCursor.close();
                        }
                    }
                    if (mode != UserPreferences.CardSourceMode.REVIEW) {
                        Collections.shuffle(cards);
                    }
                    // Only skip the already-shown card in random modes; in REVIEW mode we must keep the
                    // exact top-of-queue card to avoid oscillating between two items when refreshing.
                    if (mode != UserPreferences.CardSourceMode.REVIEW) {
                        StoredState current = getStoredState();
                        if (current != null && cards.size() > 1) {
                            for (int i = cards.size() - 1; i >= 0; i--) {
                                CardInfo c = cards.get(i);
                                if (c.noteID == current.noteID && c.cardOrd == current.cardOrd) {
                                    cards.remove(i);
                                }
                            }
                            if (cards.isEmpty()) {
                                return null;
                            }
                        }
                    }
                    return cards.get(0);
                }
            }
        }
        return null;
    }

    public CardInfo getTopCardForDeck(long deckId) {
        CardInfo card = queryCurrentScheduledCard(deckId);
        return card;
    }

    public List<CardInfo> fetchQueueCards(long deckId, boolean shuffle) {
        ArrayList<CardInfo> cards = new ArrayList<>();
        String[] deckArguments = new String[deckId == -1 ? 1 : 2];
        String deckSelector = "limit=?";
        deckArguments[0] = "" + 50;
        if (deckId != -1) {
            deckSelector += ",deckID=?";
            deckArguments[1] = "" + deckId;
        }
        if (!isPermissionGranted()) {
            return cards;
        }
        Cursor reviewInfoCursor =
                mContext.getContentResolver().query(FlashCardsContract.ReviewInfo.CONTENT_URI, null, deckSelector, deckArguments, null);

        if (reviewInfoCursor == null || !reviewInfoCursor.moveToFirst()) {
            if (reviewInfoCursor != null) {
                reviewInfoCursor.close();
            }
            return cards;
        }

        try {
            do {
                CardInfo card = new CardInfo();
                card.cardOrd = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD));
                card.noteID = reviewInfoCursor.getLong(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID));
                card.buttonCount = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.BUTTON_COUNT));

                try {
                    card.fileNames = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.MEDIA_FILES)));
                    card.nextReviewTexts = new JSONArray(reviewInfoCursor.getString(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                card.cardStartTime = System.currentTimeMillis();
                long modelId = fetchModelId(card.noteID);
                card.modelId = modelId;
                cards.add(card);
            } while (reviewInfoCursor.moveToNext());
        } finally {
            reviewInfoCursor.close();
        }

        // Apply template filter
        ArrayList<CardInfo> filtered = new ArrayList<>();
        final Set<TemplateKey> allowedTemplates = UserPreferences.INSTANCE.getTemplateFilter(mContext);
        for (CardInfo card : cards) {
            if (allowedTemplates.isEmpty() || templateMatches(allowedTemplates, new TemplateKey(card.modelId, card.cardOrd))) {
                filtered.add(card);
            }
        }
        cards = filtered;
        if (cards.isEmpty()) return cards;

        // Enrich with question/answer text
        for (CardInfo card : cards) {
            Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(card.noteID));
            Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
            Uri specificCardUri = Uri.withAppendedPath(cardsUri, Integer.toString(card.cardOrd));
            final Cursor specificCardCursor = mContext.getContentResolver().query(specificCardUri,
                    CARD_PROJECTION,
                    null,
                    null,
                    null
            );
            if (specificCardCursor != null && specificCardCursor.moveToFirst()) {
                card.rawAnswer = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.ANSWER));
                card.rawQuestion = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.QUESTION));
                card.simpleAnswer = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.ANSWER_PURE));
                card.simpleQuestion = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.QUESTION_SIMPLE));
                card.a = card.rawAnswer != null && !card.rawAnswer.isEmpty() ? card.rawAnswer : card.simpleAnswer;
                card.q = card.rawQuestion != null && !card.rawQuestion.isEmpty() ? card.rawQuestion : card.simpleQuestion;
            }
            if (specificCardCursor != null) {
                specificCardCursor.close();
            }
        }

        if (shuffle) {
            Collections.shuffle(cards);
        }
        return cards;
    }

    public List<CardInfo> fetchRandomDeckCards(long deckId, int targetCount, int sampleLimit) {
        return fetchRandomDeckCardsInternal(deckId, targetCount, sampleLimit, false, true);
    }

    public List<CardInfo> fetchRandomDeckCardsNoReview(long deckId, int targetCount, int sampleLimit) {
        return fetchRandomDeckCardsInternal(deckId, targetCount, sampleLimit, false, false);
    }

    private List<CardInfo> fetchRandomDeckCardsInternal(long deckId, int targetCount, int sampleLimit, boolean ignoreTemplateFilter, boolean useReviewQueue) {
        ArrayList<CardInfo> result = new ArrayList<>();
        if (deckId == -1L || !isPermissionGranted()) {
            return result;
        }
        int cap = Math.max(1, sampleLimit);
        ArrayList<Long> noteIds = new ArrayList<>();
        // Filter notes by deckId explicitly to avoid pulling other decks.
        Cursor reviewCursor = null;
        if (useReviewQueue) {
            try {
                reviewCursor = mContext.getContentResolver().query(
                        FlashCardsContract.ReviewInfo.CONTENT_URI,
                        new String[]{FlashCardsContract.ReviewInfo.NOTE_ID},
                        "did=?",
                        new String[]{String.valueOf(deckId)},
                        null
                );
            } catch (IllegalArgumentException e) {
                Log.w("AnkiDroidHelper", "Review deck filter not supported with did: " + e.getMessage());
                try {
                    reviewCursor = mContext.getContentResolver().query(
                            FlashCardsContract.ReviewInfo.CONTENT_URI,
                            new String[]{FlashCardsContract.ReviewInfo.NOTE_ID, "did"},
                            null,
                            null,
                            null
                    );
                } catch (IllegalArgumentException ex) {
                    Log.w("AnkiDroidHelper", "Review table query unsupported: " + ex.getMessage());
                    reviewCursor = null;
                }
            }
        }
        if (reviewCursor != null) {
            try {
                int noteIdx = reviewCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID);
                int didIdx = reviewCursor.getColumnIndex("did");
                if (noteIdx != -1 && reviewCursor.moveToFirst()) {
                    do {
                        if (noteIds.size() >= cap) break;
                        if (didIdx != -1) {
                            long did = reviewCursor.getLong(didIdx);
                            if (did != deckId) continue;
                        }
                        noteIds.add(reviewCursor.getLong(noteIdx));
                    } while (reviewCursor.moveToNext());
                }
            } finally {
                reviewCursor.close();
            }
        }
        int reviewPulled = noteIds.size();
        int noteTablePulled = noteIds.size() - reviewPulled;
        // If still insufficient, pull note ids from card table by deck to widen coverage (roam).
        if (noteIds.size() < cap) {
            Cursor cardCursor = null;
            try {
                // Prefer the official card URI and deck column constant.
                Uri cardsUri = Uri.parse("content://com.ichi2.anki.flashcards/cards");
                cardCursor = mContext.getContentResolver().query(
                        cardsUri,
                        new String[]{FlashCardsContract.Card.NOTE_ID, FlashCardsContract.Card.DECK_ID},
                        FlashCardsContract.Card.DECK_ID + "=?",
                        new String[]{String.valueOf(deckId)},
                        null
                );
                if (cardCursor == null) {
                    // Fallback to unfiltered query; we'll filter manually if the deck column exists.
                    cardCursor = mContext.getContentResolver().query(
                            cardsUri,
                            new String[]{FlashCardsContract.Card.NOTE_ID, FlashCardsContract.Card.DECK_ID},
                            null,
                            null,
                            null
                    );
                }
                if (cardCursor != null) {
                    int noteIdx = cardCursor.getColumnIndex(FlashCardsContract.Card.NOTE_ID);
                    int didIdx = cardCursor.getColumnIndex(FlashCardsContract.Card.DECK_ID);
                    while (cardCursor.moveToNext() && noteIds.size() < cap) {
                        if (noteIdx == -1) continue;
                        if (didIdx != -1) {
                            long did = cardCursor.getLong(didIdx);
                            if (did != deckId) continue;
                        }
                        long nid = cardCursor.getLong(noteIdx);
                        if (!noteIds.contains(nid)) {
                            noteIds.add(nid);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.w("AnkiDroidHelper", "Card deck filter not supported: " + e.getMessage());
            } finally {
                if (cardCursor != null) cardCursor.close();
            }
        }
        int cardTablePulled = noteIds.size() - reviewPulled - noteTablePulled;

        // Fallback: if still insufficient, try note search by deck name and filter cards by deckId.
        int deckSearchPulled = 0;
        if (noteIds.size() < cap) {
            String deckName = null;
            try {
                deckName = mApi.getDeckName(deckId);
            } catch (Exception ignored) {
            }
            if (deckName != null && !deckName.isEmpty()) {
                String escaped = deckName.replace("\"", "\\\"");
                String search = "deck:\"" + escaped + "\"";
                Cursor searchCursor = null;
                try {
                    searchCursor = mContext.getContentResolver().query(
                            FlashCardsContract.Note.CONTENT_URI,
                            new String[]{FlashCardsContract.Note._ID},
                            search,
                            null,
                            null
                    );
                    if (searchCursor != null) {
                        int idIdx = searchCursor.getColumnIndex(FlashCardsContract.Note._ID);
                        while (searchCursor.moveToNext() && noteIds.size() < cap) {
                            if (idIdx == -1) continue;
                            long nid = searchCursor.getLong(idIdx);
                            // Ensure this note has at least one card in the target deck.
                            Uri noteBase = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(nid));
                            Uri cardsUri = Uri.withAppendedPath(noteBase, "cards");
                            Cursor cardCursor = null;
                            boolean inDeck = false;
                            try {
                                cardCursor = mContext.getContentResolver().query(
                                        cardsUri,
                                        new String[]{FlashCardsContract.Card.DECK_ID},
                                        null,
                                        null,
                                        null
                                );
                                if (cardCursor != null) {
                                    int didIdx = cardCursor.getColumnIndex(FlashCardsContract.Card.DECK_ID);
                                    while (cardCursor.moveToNext()) {
                                        if (didIdx != -1 && cardCursor.getLong(didIdx) == deckId) {
                                            inDeck = true;
                                            break;
                                        }
                                    }
                                }
                            } catch (IllegalArgumentException e) {
                                Log.w("AnkiDroidHelper", "Note search card filter failed for note " + nid + ": " + e.getMessage());
                            } finally {
                                if (cardCursor != null) cardCursor.close();
                            }
                            if (inDeck && !noteIds.contains(nid)) {
                                noteIds.add(nid);
                                deckSearchPulled++;
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    Log.w("AnkiDroidHelper", "Note search by deck not supported: " + e.getMessage());
                } finally {
                    if (searchCursor != null) searchCursor.close();
                }
            }
        }

        if (noteIds.isEmpty()) return result;
        Collections.shuffle(noteIds);
        final Set<TemplateKey> storedTemplates = ignoreTemplateFilter
                ? java.util.Collections.emptySet()
                : UserPreferences.INSTANCE.getTemplateFilter(mContext);
        Set<TemplateKey> allowedTemplates = storedTemplates;
        if (!ignoreTemplateFilter && !storedTemplates.isEmpty()) {
            // Workaround: if template list for this deck cannot be loaded or has no overlap, ignore filter.
            List<TemplateOption> deckTemplates = getTemplateOptionsForDeck(deckId);
            if (deckTemplates.isEmpty()) {
                allowedTemplates = java.util.Collections.emptySet();
            } else {
                Set<TemplateKey> deckKeys = new LinkedHashSet<>();
                for (TemplateOption opt : deckTemplates) {
                    deckKeys.add(new TemplateKey(opt.modelId, opt.ord));
                }
                boolean overlaps = false;
                for (TemplateKey key : storedTemplates) {
                    if (deckKeys.contains(key)) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    allowedTemplates = java.util.Collections.emptySet();
                }
            }
        }
        Map<Long, Integer> modelCardCountCache = new HashMap<>();

        Log.d("AnkiDroidHelper", "fetchRandomDeckCards: deckId=" + deckId + " noteCount=" + noteIds.size() + " target=" + targetCount + " sampleLimit=" + cap + " templateFilterSize=" + allowedTemplates.size() + " ignoreFilter=" + ignoreTemplateFilter + " reviewPull=" + reviewPulled + " noteTablePull=" + noteTablePulled + " cardTablePull=" + cardTablePulled + " deckSearchPull=" + deckSearchPulled);
        for (Long noteId : noteIds) {
            if (result.size() >= targetCount) break;
            long modelId = fetchModelId(noteId);
            if (modelId == -1) continue;
            int numCards = getModelCardCount(modelId, modelCardCountCache);
            if (numCards <= 0) continue;

            ArrayList<Integer> ords = new ArrayList<>();
            for (int ord = 0; ord < numCards; ord++) {
                if (allowedTemplates.isEmpty() || templateMatches(allowedTemplates, new TemplateKey(modelId, ord))) {
                    ords.add(ord);
                }
            }
            if (ords.isEmpty()) continue;
            Collections.shuffle(ords);
            Uri noteBase = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
            Uri cardsUri = Uri.withAppendedPath(noteBase, "cards");
            for (Integer ord : ords) {
                if (result.size() >= targetCount) break;
                Uri specificCardUri = Uri.withAppendedPath(cardsUri, Integer.toString(ord));
                Cursor specificCardCursor = null;
                try {
                    specificCardCursor = mContext.getContentResolver().query(
                            specificCardUri,
                            CARD_PROJECTION,
                            null,
                            null,
                            null
                    );
                    if (specificCardCursor != null && specificCardCursor.moveToFirst()) {
                        CardInfo card = new CardInfo();
                        card.cardOrd = ord;
                        card.noteID = noteId;
                        card.modelId = modelId;
                        card.rawAnswer = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.ANSWER));
                        card.rawQuestion = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.QUESTION));
                        card.simpleAnswer = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.ANSWER_PURE));
                        card.simpleQuestion = specificCardCursor.getString(specificCardCursor.getColumnIndex(FlashCardsContract.Card.QUESTION_SIMPLE));
                        card.a = card.rawAnswer != null && !card.rawAnswer.isEmpty() ? card.rawAnswer : card.simpleAnswer;
                        card.q = card.rawQuestion != null && !card.rawQuestion.isEmpty() ? card.rawQuestion : card.simpleQuestion;
                        card.cardStartTime = System.currentTimeMillis();
                        result.add(card);
                    } else {
                        Log.w("AnkiDroidHelper", "No card for note " + noteId + " ord " + ord + ", skipping.");
                    }
                } catch (IllegalArgumentException e) {
                    Log.w("AnkiDroidHelper", "Query failed for note " + noteId + " ord " + ord + ": " + e.getMessage());
                } catch (Exception e) {
                    Log.w("AnkiDroidHelper", "Unexpected error for note " + noteId + " ord " + ord + ": " + e.getMessage());
                } finally {
                    if (specificCardCursor != null) {
                        specificCardCursor.close();
                    }
                }
            }
        }
        Log.d("AnkiDroidHelper", "fetchRandomDeckCards: built cards=" + result.size() + " ignoreFilter=" + ignoreTemplateFilter);
        if (result.isEmpty() && !ignoreTemplateFilter && !UserPreferences.INSTANCE.getTemplateFilter(mContext).isEmpty()) {
            Log.w("AnkiDroidHelper", "No random cards matched template filter; falling back to all templates for deck " + deckId);
            return fetchRandomDeckCardsInternal(deckId, targetCount, cap, true, useReviewQueue);
        }
        return result;
    }

    private int getModelCardCount(long modelId, Map<Long, Integer> cache) {
        if (cache.containsKey(modelId)) return cache.get(modelId);
        int numCards = 0;
        Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
        Cursor modelCursor = mContext.getContentResolver().query(
                modelUri,
                new String[]{FlashCardsContract.Model.NUM_CARDS},
                null,
                null,
                null
        );
        if (modelCursor != null) {
            try {
                if (modelCursor.moveToFirst()) {
                    numCards = modelCursor.getInt(modelCursor.getColumnIndex(FlashCardsContract.Model.NUM_CARDS));
                }
            } finally {
                modelCursor.close();
            }
        }
        cache.put(modelId, numCards);
        return numCards;
    }

    public boolean reviewCard(long noteID, int cardOrd, long cardStartTime, int ease) {
        long timeTaken = System.currentTimeMillis() - cardStartTime;
        ContentResolver cr = mContext.getContentResolver();
        Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
        ContentValues values = new ContentValues();
        values.put(FlashCardsContract.ReviewInfo.NOTE_ID, noteID);
        values.put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd);
        values.put(FlashCardsContract.ReviewInfo.EASE, ease);
        values.put(FlashCardsContract.ReviewInfo.TIME_TAKEN, timeTaken);
        // Log.d(TAG, timeTaken + " time taken " + values.getAsLong(FlashCardsContract.ReviewInfo.TIME_TAKEN));
        try {
            cr.update(reviewInfoUri, values, null, null);
            return true;
        } catch (RuntimeException e) {
            Log.w("AnkiDroidHelper", "Failed to submit review, card may have changed", e);
            return false;
        }
    }

    private long fetchModelId(long noteId) {
        Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
        Cursor cursor = mContext.getContentResolver().query(
                noteUri,
                new String[]{FlashCardsContract.Note.MID},
                null,
                null,
                null
        );
        if (cursor == null) {
            return -1;
        }
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndex(FlashCardsContract.Note.MID));
            }
        } finally {
            cursor.close();
        }
        return -1;
    }

    private boolean templateMatches(Set<TemplateKey> allowed, TemplateKey candidate) {
        for (TemplateKey key : allowed) {
            if (key.modelId >= 0) {
                if (key.modelId == candidate.modelId && key.ord == candidate.ord) {
                    return true;
                }
            } else {
                // backward compatibility: match on ord only
                if (key.ord == candidate.ord) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<TemplateOption> getTemplateOptionsForDeck(long deckId) {
        List<TemplateOption> result = new ArrayList<>();
        if (deckId == -1L || !isPermissionGranted()) {
            return result;
        }

        // Collect models seen in the current deck's review queue. This avoids picking up templates
        // from other decks when provider parameters are ignored.
        Set<Long> modelIds = new LinkedHashSet<>();
        String selection = "deckID=?";
        String[] args = new String[]{String.valueOf(deckId)};
        Cursor reviewCursor = mContext.getContentResolver().query(
                FlashCardsContract.ReviewInfo.CONTENT_URI,
                new String[]{FlashCardsContract.ReviewInfo.NOTE_ID},
                selection,
                args,
                null
        );
        if (reviewCursor != null) {
            try {
                int noteIdx = reviewCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID);
                if (noteIdx != -1 && reviewCursor.moveToFirst()) {
                    do {
                        long noteId = reviewCursor.getLong(noteIdx);
                        long modelId = fetchModelId(noteId);
                        if (modelId != -1) {
                            modelIds.add(modelId);
                        }
                    } while (reviewCursor.moveToNext());
                }
            } finally {
                reviewCursor.close();
            }
        }

        if (modelIds.isEmpty()) {
            return result;
        }

        // Build the full template list from those models (model name + template ord), dedup by (modelId, ord).
        Set<TemplateKey> templateKeys = new LinkedHashSet<>();
        for (Long modelId : modelIds) {
            Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
            Cursor modelCursor = mContext.getContentResolver().query(
                    modelUri,
                    new String[]{FlashCardsContract.Model.NAME, FlashCardsContract.Model.NUM_CARDS},
                    null,
                    null,
                    null
            );
            if (modelCursor != null) {
                try {
                    if (modelCursor.moveToFirst()) {
                        String modelName = modelCursor.getString(modelCursor.getColumnIndex(FlashCardsContract.Model.NAME));
                        int numCards = modelCursor.getInt(modelCursor.getColumnIndex(FlashCardsContract.Model.NUM_CARDS));
                        for (int ord = 0; ord < numCards; ord++) {
                            TemplateKey key = new TemplateKey(modelId, ord);
                            if (templateKeys.add(key)) {
                                result.add(new TemplateOption(modelId, ord, null, modelName));
                            }
                        }
                    }
                } finally {
                    modelCursor.close();
                }
            }
        }

        return result;
    }
}
