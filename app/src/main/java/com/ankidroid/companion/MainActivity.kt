package com.ankidroid.companion

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import android.widget.Toast
import android.widget.Switch
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
        if (!UserPreferences.getNotificationsEnabled(this)) {
            WorkManager.getInstance(this).cancelUniqueWork("WORKER_ANKI")
            return
        }
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
            } else if (!hasMediaPermissions()) {
                requestMediaPermissions()
            } else {
                // READ_MEDIA_xxx will be requested by the system when media is accessed to avoid double dialogs
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
        setNotificationsToggle()
        setWidgetIntervalInput()
        setAnswerLinesInput()
        scheduleWidgetRefresh()

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
        findViewById<TextView>(R.id.sharedSectionLabel).visibility = View.GONE
        findViewById<TextView>(R.id.notificationSectionLabel).visibility = View.GONE
        findViewById<TextView>(R.id.widgetSectionLabel).visibility = View.GONE
        findViewById<TextView>(R.id.notificationsLabel).visibility = View.GONE
        findViewById<Switch>(R.id.notificationsToggle).visibility = View.GONE
        findViewById<TextView>(R.id.notificationsHint).visibility = View.GONE
        findViewById<TextView>(R.id.widgetIntervalLabel).visibility = View.GONE
        findViewById<EditText>(R.id.widgetIntervalInput).visibility = View.GONE
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
                    if (hasMediaPermissions()) startApp() else requestMediaPermissions()
                } else {
                    explainError("AnkiDroid Read Write permission is not granted, please make sure that it is given!")
                }
            } else if (permission == android.Manifest.permission.READ_MEDIA_IMAGES ||
                permission == android.Manifest.permission.READ_MEDIA_AUDIO ||
                permission == android.Manifest.permission.READ_EXTERNAL_STORAGE) {
                if (hasMediaPermissions() && mAnkiDroid.isPermissionGranted) {
                    startApp()
                }
            }
        }
    }

    private fun hasMediaPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestMediaPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_AUDIO
            ), 1)
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1)
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
                    // Clear template selections from the previously chosen deck
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
        findViewById<TextView>(R.id.sharedSectionLabel).visibility = View.VISIBLE
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
        findViewById<TextView>(R.id.sharedSectionLabel).visibility = View.VISIBLE
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
        // Default to all templates for this deck if none selected yet (after deck switch clear)
        val selected = if (stored.isEmpty()) {
            available.map { TemplateKey(it.modelId, it.ord) }.toSet().also {
                UserPreferences.saveTemplateFilter(this, it)
            }
        } else {
            stored
        }

        available.forEach { option ->
            val cb = CheckBox(this)
            cb.text = option.displayName()
            cb.isChecked = selected.any { matchesTemplateSelection(it, option) }
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
        findViewById<TextView>(R.id.notificationSectionLabel).visibility = View.VISIBLE
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

    private fun setNotificationsToggle() {
        findViewById<TextView>(R.id.notificationSectionLabel).visibility = View.VISIBLE
        val label = findViewById<TextView>(R.id.notificationsLabel)
        val toggle = findViewById<Switch>(R.id.notificationsToggle)
        val hint = findViewById<TextView>(R.id.notificationsHint)
        label.visibility = View.VISIBLE
        toggle.visibility = View.VISIBLE
        hint.visibility = View.VISIBLE
        toggle.isChecked = UserPreferences.getNotificationsEnabled(this)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            UserPreferences.saveNotificationsEnabled(this, isChecked)
            if (isChecked) {
                startPeriodicWorker()
            } else {
                WorkManager.getInstance(this).cancelUniqueWork("WORKER_ANKI")
            }
        }
    }

    private fun setWidgetIntervalInput() {
        findViewById<TextView>(R.id.widgetSectionLabel).visibility = View.VISIBLE
        val label = findViewById<TextView>(R.id.widgetIntervalLabel)
        val input = findViewById<EditText>(R.id.widgetIntervalInput)
        label.visibility = View.VISIBLE
        input.visibility = View.VISIBLE
        input.setText(UserPreferences.getWidgetIntervalMinutes(this).toString())
        input.addTextChangedListener { editable ->
            val value = editable?.toString()?.toIntOrNull()
            if (value != null) {
                UserPreferences.saveWidgetIntervalMinutes(this, value)
                scheduleWidgetRefresh()
            }
        }
    }

    private fun scheduleWidgetRefresh() {
        val minutes = UserPreferences.getWidgetIntervalMinutes(this).toLong()
        val request = PeriodicWorkRequest.Builder(
            WidgetRefreshWorker::class.java,
            minutes, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WIDGET_ROTATE",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
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
            if (UserPreferences.getNotificationsEnabled(this)) {
                Notifications.create().showNotification(this, card, deckName, false)
            }
        } else {
            // No cards to show.
            val emptyCard = CardInfo()
            emptyCard.cardOrd = -1
            emptyCard.noteID = -1
            mAnkiDroid.storeState(deckID, emptyCard)
            if (UserPreferences.getNotificationsEnabled(this)) {
                Notifications.create().showNotification(this, null, "", false)
            }
        }

        // Start the periodic worker when the first card is assigned.
        startPeriodicWorker()
        scheduleWidgetRefresh()
        // Trigger an immediate widget refresh to reflect latest card/state.
        CompanionWidgetProvider().onReceive(
            this,
            Intent(CompanionWidgetProvider.ACTION_REFRESH).apply { setClass(this@MainActivity, CompanionWidgetProvider::class.java) }
        )
    }

}
