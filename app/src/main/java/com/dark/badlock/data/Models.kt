package com.dark.badlock.data

import android.content.Intent

data class ModuleInfo(
    val name: String,
    val packageName: String,
    val category: String,
    val apkMirrorMainPage: String
)

data class InstalledModule(
    val name: String,
    val packageName: String,
    val versionName: String?,
    val latestVersion: String?,
    val latestVersionUrl: String?,
    val latestVariantUrl: String? = null,
    val minAndroidVersion: String?,
    @Transient var launchIntent: Intent?, // Ignored by cache
    val isInstalled: Boolean,
    val isUpdateAvailable: Boolean,
    val category: String,
    val apkMirrorMainPage: String,
    val iconResId: Int?,
    val lastChecked: Long = 0L
)

data class AppUpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String?
)

data class VersionFetchResult(
    val version: String? = null,
    val url: String? = null,
    val variantUrl: String? = null,
    val minAndroidVersion: String? = null
)

sealed interface ModuleState {
    object Loading : ModuleState
    data class Success(val modules: Map<String, List<InstalledModule>>) : ModuleState
    data class Error(val message: String) : ModuleState
}
