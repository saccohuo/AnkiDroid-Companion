package com.ankidroid.companion

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        CompanionWidgetProvider().onReceive(
            applicationContext,
            android.content.Intent(CompanionWidgetProvider.ACTION_REFRESH).apply {
                setClass(applicationContext, CompanionWidgetProvider::class.java)
            }
        )
        return Result.success()
    }
}
