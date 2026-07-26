package com.dark.badlock.logic

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dark.badlock.data.CacheManager
import com.dark.badlock.data.ModuleRepository

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("UpdateWorker", "Starting background update check (silent cache refresh)...")
        
        try {
            val cacheManager = CacheManager(applicationContext)
            val repository = ModuleRepository(applicationContext, cacheManager)
            
            // Silently refresh the cache without showing notifications
            repository.loadData(forceRefresh = true)
            
            Log.d("UpdateWorker", "Background update check completed successfully.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Background update check failed", e)
            return Result.failure()
        }
    }
}
