package com.dark.badlock.data

import android.content.Context
import android.content.SharedPreferences
import com.dark.badlock.logic.LaunchHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CacheManager(context: Context) {
    private val prefs = context.getSharedPreferences("BadlockCache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var lastLoadedState: ModuleState.Success? = null

    fun save(state: ModuleState.Success) {
        val json = gson.toJson(state.modules)
        prefs.edit()
            .putString("cached_modules", json)
            .putLong("last_refresh_time", System.currentTimeMillis())
            .apply()
        lastLoadedState = state // Keep in-memory copy
    }

    fun load(context: Context): ModuleState.Success? {
        if (lastLoadedState != null) return lastLoadedState

        val json = prefs.getString("cached_modules", null) ?: return null
        val type = object : TypeToken<Map<String, List<InstalledModule>>>() {}.type
        val modules: Map<String, List<InstalledModule>> = gson.fromJson(json, type)

        // Rebuild launch intents as they are not cached
        val modulesWithIntents = modules.mapValues { entry ->
            entry.value.map { module ->
                if (module.isInstalled) {
                    module.apply {
                        launchIntent = LaunchHelper.getBestLaunchIntent(context, module.packageName, module.name)
                    }
                } else {
                    module
                }
            }
        }
        val state = ModuleState.Success(modulesWithIntents)
        lastLoadedState = state
        return state
    }

    fun getLastRefreshTime(): Long {
        return prefs.getLong("last_refresh_time", 0L)
    }
}
