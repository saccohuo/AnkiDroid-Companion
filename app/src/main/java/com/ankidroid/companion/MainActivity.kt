package com.ankidroid.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.widget.addTextChangedListener
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.ichi2.anki.api.AddContentApi
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {
    private lateinit var mAnkiDroid:AnkiDroidHelper
    private var currentDeckId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)
        createNotificationChannel()
        setup()
    }

    private fun createNotificationChannel() {
        val name = "AnkiNotificationChannel"
        val descriptionText = "Channel for anki notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("channel_id", name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun startPeriodicWorker() {
        Log.i("BackgroundService", "startBackgroundService called from MainActivity")
        val periodicWorkRequest = PeriodicWorkRequest.Builder(
            PeriodicWorker::class.java,
            8, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WORKER_ANKI",
            ExistingPeriodicWorkPolicy.REPLACE,  // Use REPLACE to ensure only one instance is scheduled
            periodicWorkRequest
        )
    }

    private fun setup() {
        // Api is not available, either AnkiDroid is not installed or API is disabled.
        if (!AnkiDroidHelper.isApiAvailable(this)) {
            explainError("API is not available!\n" +
                    "This means either AnkiDroid is not installed or API is disabled from the AnkiDroid app")
        } else {
            mAnkiDroid = AnkiDroidHelper(this)
            if (mAnkiDroid.shouldRequestPermission()) {
                explainError("AnkiDroid Read Write permission is not granted, please make sure that it is given!")
                // requestPermissionLauncher.launch(AddContentApi.READ_WRITE_PERMISSION)
                mAnkiDroid.requestPermission(this, 0)
            } else {
                startApp()
            }
        }
    }

    private fun startApp() {
        val text = findViewById<TextView>(R.id.mainTextView)
        text.text = "Select a deck:"
        text.visibility = View.VISIBLE

        setDecksSpinner()
        setFieldModeSpinner()
        setTemplateFilterCheckboxes()
        setAnswerLinesInput()

        val button = findViewById<Button>(R.id.mainRefreshButton)
        button.visibility = View.VISIBLE
        button.setOnClickListener{
            onClickRefresh()
        }
    }

    private fun explainError(errorText:String) {
        val text = findViewById<TextView>(R.id.mainTextView)
        text.text = errorText
        text.visibility = View.VISIBLE
        findViewById<Spinner>(R.id.spinner1).visibility = View.GONE
        findViewById<TextView>(R.id.fieldModeLabel).visibility = View.GONE
        findViewById<Spinner>(R.id.fieldModeSpinner).visibility = View.GONE
        findViewById<TextView>(R.id.templateFilterLabel).visibility = View.GONE
        findViewById<View>(R.id.templateCheckboxContainer).visibility = View.GONE
        findViewById<TextView>(R.id.answerLinesLabel).visibility = View.GONE
        findViewById<EditText>(R.id.answerLinesInput).visibility = View.GONE
        findViewById<Button>(R.id.mainRefreshButton).visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        for ((index, _) in permissions.withIndex()) {
            val permission = permissions[index]
            val grantResult = grantResults[index]
            if (permission == AddContentApi.READ_WRITE_PERMISSION) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    startApp()
                } else {
                    explainError("AnkiDroid Read Write permission is not granted, please make sure that it is given!")
                }
            }
        }
    }

    private fun setDecksSpinner() {
        val items = mutableListOf<String>()
        var startIndex = 0
        var lastDeckId:Long = -1
        val deckList = mAnkiDroid.api.deckList

        val localState = mAnkiDroid.storedState
        if (localState != null) {
            lastDeckId = localState.deckId
        }

        var count = 0
        if (deckList != null) {
            for (item in deckList) {
                items.add(item.value)
                if (item.key == lastDeckId) {
                    startIndex = count
                }
                count++
            }
        }

        val dropdown = findViewById<Spinner>(R.id.spinner1)
        dropdown.visibility = View.VISIBLE
        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items)
        dropdown.adapter = adapter
        dropdown.setSelection(startIndex)

        dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedDeckName = items.getOrNull(position) ?: return
                val deckId = mAnkiDroid.findDeckIdByName(selectedDeckName)
                if (deckId != currentDeckId) {
                    // 清空上一牌组的模板选择
                    UserPreferences.saveTemplateFilter(this@MainActivity, emptySet())
                    currentDeckId = deckId
                }
                updateTemplateFilterCheckboxes(deckId)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // no-op
            }
        }

        // Initialize template checkboxes for preselected deck.
        val initialDeckName = items.getOrNull(startIndex)
        if (initialDeckName != null) {
            val deckId = mAnkiDroid.findDeckIdByName(initialDeckName)
            currentDeckId = deckId
            updateTemplateFilterCheckboxes(deckId)
        }
    }

    private fun setFieldModeSpinner() {
        val spinner = findViewById<Spinner>(R.id.fieldModeSpinner)
        spinner.visibility = View.VISIBLE
        findViewById<TextView>(R.id.fieldModeLabel).visibility = View.VISIBLE

        val options = listOf(
            getString(R.string.field_mode_both),
            getString(R.string.field_mode_question),
            getString(R.string.field_mode_answer)
        )

        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.adapter = adapter

        val currentMode = UserPreferences.getFieldMode(this)
        val startIndex = when (currentMode) {
            FieldMode.BOTH -> 0
            FieldMode.QUESTION_ONLY -> 1
            FieldMode.ANSWER_ONLY -> 2
        }
        spinner.setSelection(startIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val mode = when (position) {
                    0 -> FieldMode.BOTH
                    1 -> FieldMode.QUESTION_ONLY
                    else -> FieldMode.ANSWER_ONLY
                }
                UserPreferences.saveFieldMode(this@MainActivity, mode)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // no-op
            }
        }
    }

    private fun setTemplateFilterCheckboxes() {
        val container = findViewById<View>(R.id.templateCheckboxContainer)
        container.visibility = View.VISIBLE
        findViewById<TextView>(R.id.templateFilterLabel).visibility = View.VISIBLE
    }

    private fun updateTemplateFilterCheckboxes(deckId: Long?) {
        val container = findViewById<LinearLayout>(R.id.templateCheckboxContainer)
        container.removeAllViews()

        if (deckId == null || deckId == -1L) {
            return
        }

        val available = mAnkiDroid.getTemplateOptionsForDeck(deckId)
        if (available.isEmpty()) {
            return
        }

        val stored = UserPreferences.getTemplateFilter(this).filter { key ->
            available.any { matchesTemplateSelection(key, it) }
        }.toSet()
        if (stored.size != UserPreferences.getTemplateFilter(this).size) {
            // Remove selections from other decks.
            UserPreferences.saveTemplateFilter(this, stored)
        }

        available.forEach { option ->
            val cb = CheckBox(this)
            cb.text = option.displayName()
            cb.isChecked = stored.any { matchesTemplateSelection(it, option) }
            cb.tag = option
            cb.setOnCheckedChangeListener { _, _ ->
                val selected = (0 until container.childCount).mapNotNull { idx ->
                    val child = container.getChildAt(idx) as? CheckBox
                    val tag = child?.tag as? TemplateOption
                    if (child?.isChecked == true && tag != null) TemplateKey(tag.modelId, tag.ord) else null
                }.toSet()
                UserPreferences.saveTemplateFilter(this, selected)
            }
            container.addView(cb)
        }
    }

    private fun setAnswerLinesInput() {
        val label = findViewById<TextView>(R.id.answerLinesLabel)
        val input = findViewById<EditText>(R.id.answerLinesInput)
        label.visibility = View.VISIBLE
        input.visibility = View.VISIBLE

        input.setText(UserPreferences.getContentMaxLines(this).toString())
        input.addTextChangedListener { editable ->
            val value = editable?.toString()?.toIntOrNull()
            if (value != null && value in 1..10) {
                UserPreferences.saveContentMaxLines(this, value)
            }
        }
    }

    private fun matchesTemplateSelection(key: TemplateKey, option: TemplateOption): Boolean {
        return (key.modelId >= 0 && key.modelId == option.modelId && key.ord == option.ord) ||
                (key.modelId < 0 && key.ord == option.ord)
    }

    private fun onClickRefresh() {
        val decksDropdown = findViewById<Spinner>(R.id.spinner1)
        val deckName = decksDropdown.selectedItem.toString()
        val deckID = mAnkiDroid.findDeckIdByName(deckName)
        mAnkiDroid.storeDeckReference(deckName, deckID)

        val card = mAnkiDroid.queryCurrentScheduledCard(deckID)
        if (card != null) {
            mAnkiDroid.storeState(deckID, card)
            Notifications.create().showNotification(this, card, deckName, false)
        } else {
            // No cards to show.
            val emptyCard = CardInfo()
            emptyCard.cardOrd = -1
            emptyCard.noteID = -1
            mAnkiDroid.storeState(deckID, emptyCard)
            Notifications.create().showNotification(this, null, "", false)
        }

        // Start the periodic worker when the first card is assigned.
        startPeriodicWorker()
    }
}
