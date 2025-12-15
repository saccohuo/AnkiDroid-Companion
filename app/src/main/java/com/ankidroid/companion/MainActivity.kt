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
import android.util.TypedValue
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
import android.graphics.Typeface
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

    override fun onResume() {
        super.onResume()
        // Re-evaluate deck availability when returning from AnkiDroid.
        setDecksSpinner()
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
        setWidgetModeSpinner()
        setTemplateFilterCheckboxes()
        setNotificationsToggle()
        setWidgetIntervalInput()
        setAnswerLinesInput()
        scheduleWidgetRefresh()
        // Show current app version from package info
        val versionName = try {
            val pkgInfo = packageManager.getPackageInfo(packageName, 0)
            pkgInfo.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        findViewById<TextView>(R.id.versionLabel)?.text = getString(R.string.version_label, versionName)

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
        findViewById<TextView>(R.id.widgetModeLabel).visibility = View.GONE
        findViewById<Spinner>(R.id.widgetModeSpinner).visibility = View.GONE
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
        findViewById<TextView>(R.id.widgetModeLabel).visibility = View.GONE
        findViewById<Spinner>(R.id.widgetModeSpinner).visibility = View.GONE
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

    private fun setWidgetModeSpinner() {
        val spinner = findViewById<Spinner>(R.id.widgetModeSpinner)
        spinner.visibility = View.VISIBLE
        findViewById<TextView>(R.id.widgetModeLabel).visibility = View.VISIBLE

        val options = listOf(
            getString(R.string.widget_mode_queue),
            getString(R.string.widget_mode_random)
        )

        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.adapter = adapter

        val currentMode = UserPreferences.getWidgetMode(this)
        val startIndex = when (currentMode) {
            UserPreferences.WidgetMode.QUEUE -> 0
            UserPreferences.WidgetMode.RANDOM -> 1
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
                    0 -> UserPreferences.WidgetMode.QUEUE
                    else -> UserPreferences.WidgetMode.RANDOM
                }
                UserPreferences.saveWidgetMode(this@MainActivity, mode)
                // Refresh widget to apply mode change immediately
                CompanionWidgetProvider().onReceive(
                    this@MainActivity,
                    Intent(CompanionWidgetProvider.ACTION_REFRESH).apply { setClass(this@MainActivity, CompanionWidgetProvider::class.java) }
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // no-op
            }
        }
    }

    private fun setDecksSpinner() {
        val items = mutableListOf<String>()
        var startIndex = 0
        var lastDeckId:Long = -1
        var deckList = mAnkiDroid.api.deckList
        // If AnkiDroid was killed and restarted, re-initialize helper and retry.
        if (deckList == null || deckList.isEmpty()) {
            mAnkiDroid = AnkiDroidHelper(this)
            deckList = mAnkiDroid.api.deckList
        }

        if (deckList == null || deckList.isEmpty()) {
            // Show only the hint and the refresh button so the user can retry after opening AnkiDroid.
            findViewById<Spinner>(R.id.spinner1).visibility = View.GONE
            findViewById<TextView>(R.id.mainTextView).apply {
                text = getString(R.string.no_decks_hint)
                visibility = View.VISIBLE
                setTypeface(null, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            findViewById<Button>(R.id.mainRefreshButton).visibility = View.VISIBLE
            // Clear template UI when no decks are available
            updateTemplateFilterCheckboxes(null)
            // Hide other controls to reduce confusion until decks are available again
            toggleDeckDependentViews(false)
            return
        }

        // Ensure the main label is back to the normal prompt when decks are available again.
        findViewById<TextView>(R.id.mainTextView).apply {
            text = "Select a deck:"
            visibility = View.VISIBLE
            setTypeface(null, Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        findViewById<Button>(R.id.mainRefreshButton).visibility = View.VISIBLE
        toggleDeckDependentViews(true)

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
        // If no decks loaded yet, try to reload (e.g., after opening AnkiDroid)
        var deckList = mAnkiDroid.api.deckList
        if (deckList == null || deckList.isEmpty()) {
            setDecksSpinner()
            deckList = mAnkiDroid.api.deckList
            if (deckList == null || deckList.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_decks_hint), Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (decksDropdown.visibility != View.VISIBLE || decksDropdown.adapter == null || decksDropdown.adapter.count == 0 || decksDropdown.selectedItem == null) {
            Toast.makeText(this, getString(R.string.no_decks_hint), Toast.LENGTH_SHORT).show()
            return
        }

        val deckName = decksDropdown.selectedItem.toString()
        val deckID = mAnkiDroid.findDeckIdByName(deckName)
        if (deckID == null || deckID == -1L) {
            Toast.makeText(this, getString(R.string.no_decks_hint), Toast.LENGTH_SHORT).show()
            return
        }
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

    private fun toggleDeckDependentViews(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        findViewById<Spinner>(R.id.spinner1).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.fieldModeLabel).visibility = visibility
        findViewById<Spinner>(R.id.fieldModeSpinner).visibility = visibility
        findViewById<TextView>(R.id.widgetModeLabel).visibility = visibility
        findViewById<Spinner>(R.id.widgetModeSpinner).visibility = visibility
        findViewById<TextView>(R.id.templateFilterLabel).visibility = visibility
        findViewById<View>(R.id.templateCheckboxContainer).visibility = visibility
        findViewById<TextView>(R.id.sharedSectionLabel).visibility = visibility
        findViewById<TextView>(R.id.notificationSectionLabel).visibility = visibility
        findViewById<TextView>(R.id.widgetSectionLabel).visibility = visibility
        findViewById<TextView>(R.id.notificationsLabel).visibility = visibility
        findViewById<Switch>(R.id.notificationsToggle).visibility = visibility
        findViewById<TextView>(R.id.notificationsHint).visibility = visibility
        findViewById<TextView>(R.id.widgetIntervalLabel).visibility = visibility
        findViewById<EditText>(R.id.widgetIntervalInput).visibility = visibility
        findViewById<TextView>(R.id.answerLinesLabel).visibility = visibility
        findViewById<EditText>(R.id.answerLinesInput).visibility = visibility
    }

}
