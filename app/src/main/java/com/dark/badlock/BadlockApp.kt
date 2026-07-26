package com.dark.badlock

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dark.badlock.logic.UpdateWorker
import java.util.concurrent.TimeUnit

class BadlockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        setupBackgroundUpdateCheck()
    }

    private fun setupBackgroundUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateWorkRequest = PeriodicWorkRequestBuilder<UpdateWorker>(3, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpdateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWorkRequest
        )
    }
}
