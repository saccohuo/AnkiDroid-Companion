package com.ankidroid.companion;

import android.net.Uri;
import org.json.JSONArray;
import java.util.ArrayList;

public class CardInfo {
    String q = "", a = "";
    String rawQuestion = "", rawAnswer = "";
    String simpleQuestion = "", simpleAnswer = "";
    int cardOrd;
    long modelId = -1;
    long noteID;
    int buttonCount;
    JSONArray nextReviewTexts = null;
    JSONArray fileNames;
    ArrayList<Uri> soundUris = null;
    long cardStartTime;

    public synchronized void addSoundUri(Uri path) {
        Uri uri = path;//Uri.fromFile(new File(path));

        if (soundUris == null) {
            soundUris = new ArrayList<>();
        }

        soundUris.add(uri);
    }
}
