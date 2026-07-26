package com.dark.badlock.logic

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.dark.badlock.data.InstalledModule

object LaunchHelper {

    fun getBestLaunchIntent(context: Context, packageName: String, moduleName: String): Intent? {
        val packageManager = context.packageManager
        try {
            val specialIntent = getSpecialLaunchIntent(context, packageName, moduleName)
            if (specialIntent != null) {
                Log.d("BadlockLaunch", "Using special intent for $moduleName: ${specialIntent.component}")
                return specialIntent
            }
            val launcherIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launcherIntent != null && !isProblematicLauncherIntent(packageName, launcherIntent)) {
                Log.d("BadlockLaunch", "Found launcher intent for $packageName: ${launcherIntent.component}")
                return launcherIntent
            }
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
            val activities = packageManager.queryIntentActivities(mainIntent, 0)
            if (activities.isNotEmpty()) {
                val goodActivity = activities.find { resolveInfo -> !isProblematicActivity(packageName, resolveInfo.activityInfo.name) } ?: activities[0]
                val activity = goodActivity.activityInfo
                val intent = Intent().apply {
                    component = ComponentName(activity.packageName, activity.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Log.d("BadlockLaunch", "Found MAIN/LAUNCHER activity for $packageName: ${intent.component}")
                return intent
            }
            return findBestActivityDeepSearch(context, packageName, moduleName)
        } catch (e: Exception) {
            Log.e("BadlockLaunch", "Error finding launch intent for $packageName", e)
            return null
        }
    }

    private fun getSpecialLaunchIntent(context: Context, packageName: String, moduleName: String): Intent? {
        return when (packageName) {
            "com.samsung.android.app.clockface" -> {
                Log.d("BadlockLaunch", "Clockface: Redirecting to WallpaperSettingActivity.")
                return Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.app.dressroom",
                        "com.samsung.android.app.dressroom.presentation.settings.WallpaperSettingActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            "com.samsung.systemui.lockstar" -> {
                val settingsLockIntent = Intent().apply {
                    action = "android.intent.action.MAIN"
                    component = ComponentName(
                        "com.android.settings",
                        "com.samsung.android.settings.lockscreen.LockScreenSettings"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(settingsLockIntent, 0) != null) {
                    return settingsLockIntent
                }

                val possibleActivities = listOf(
                    "com.samsung.systemui.lockstar.presentation.ui.LockStarActivity",
                    "com.samsung.systemui.lockstar.presentation.main.LockStarActivity",
                    "com.samsung.systemui.lockstar.LockStarActivity",
                    "com.samsung.systemui.lockstar.MainActivity"
                )
                return findWorkingActivity(context, packageName, possibleActivities)
            }
            "com.samsung.android.app.routineplus" -> {
                val modesRoutinesIntent = Intent().apply {
                    component = ComponentName(
                        "com.android.settings",
                        "com.samsung.android.settings.routine.RoutineSettings"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(modesRoutinesIntent, 0) != null) {
                    return modesRoutinesIntent
                }

                val bixbyRoutinesIntent = Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.bixby.service",
                        "com.samsung.android.bixby.routines.ui.RoutinesMainActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return if (context.packageManager.resolveActivity(bixbyRoutinesIntent, 0) != null) {
                    bixbyRoutinesIntent
                } else null
            }
            "com.samsung.android.soundassistant" -> {
                val soundSettingsIntent = Intent().apply {
                    component = ComponentName(
                        "com.android.settings",
                        "com.samsung.android.settings.soundquality.SoundQualitySettings"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(soundSettingsIntent, 0) != null) {
                    return soundSettingsIntent
                }

                return findBestActivityDeepSearch(context, packageName, moduleName)
            }
            else -> null
        }
    }

    private fun isProblematicLauncherIntent(packageName: String, intent: Intent): Boolean {
        val component = intent.component?.className ?: return false
        return when (packageName) {
            "com.samsung.systemui.lockstar" -> {
                component.contains("shortcut", ignoreCase = true) || component.contains("widget", ignoreCase = true)
            }
            "com.samsung.android.app.routineplus" -> {
                component.contains("credit", ignoreCase = true) || component.contains("about", ignoreCase = true)
            }
            else -> false
        }
    }

    private fun isProblematicActivity(packageName: String, activityName: String): Boolean {
        val problematicKeywords = listOf("shortcut", "widget", "credit", "about", "help")
        return problematicKeywords.any { keyword ->
            activityName.contains(keyword, ignoreCase = true)
        }
    }

    private fun findWorkingActivity(context: Context, packageName: String, activityNames: List<String>): Intent? {
        val packageManager = context.packageManager

        for (activityName in activityNames) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val activityInfo = packageManager.getActivityInfo(intent.component!!, 0)
                if (activityInfo.enabled) {
                    Log.d("BadlockLaunch", "Found working activity: $activityName")
                    return intent
                }
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun findBestActivityDeepSearch(context: Context, packageName: String, moduleName: String): Intent? {
        val packageManager = context.packageManager

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = packageInfo.activities ?: return null

            val scoredActivities = activities.filter { it.exported }.map { activityInfo ->
                val score = calculateActivityScore(activityInfo.name, moduleName)
                Pair(activityInfo, score)
            }.sortedByDescending { it.second }

            val bestActivity = scoredActivities.firstOrNull()?.first
            if (bestActivity != null) {
                val intent = Intent().apply {
                    component = ComponentName(bestActivity.packageName, bestActivity.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Log.d("BadlockLaunch", "Found best activity via deep search for $packageName: ${intent.component}")
                return intent
            }

        } catch (e: Exception) {
            Log.e("BadlockLaunch", "Deep search failed for $packageName", e)
        }

        return null
    }

    private fun calculateActivityScore(activityName: String, moduleName: String): Int {
        var score = 0
        val goodKeywords = listOf("main" to 50, "home" to 40, "launcher" to 35, "settings" to 30, "ui" to 25, moduleName.lowercase().replace(" ", "") to 45)
        val badKeywords = listOf("shortcut" to -100, "widget" to -80, "credit" to -90, "about" to -70, "help" to -60, "splash" to -40, "intro" to -40)
        val lowerActivityName = activityName.lowercase()
        goodKeywords.forEach { (keyword, points) -> if (lowerActivityName.contains(keyword)) score += points }
        badKeywords.forEach { (keyword, points) -> if (lowerActivityName.contains(keyword)) score += points }
        if (lowerActivityName.endsWith("activity")) score += 10
        return score
    }

    fun launchModule(context: Context, module: InstalledModule) {
        try {
            module.launchIntent?.let { intent ->
                Log.d("BadlockLaunch", "Launching ${module.name} with intent: ${intent.component}")
                if (module.packageName == "com.samsung.android.app.clockface") {
                    AlertDialog.Builder(context)
                        .setTitle("Clockface Instructions (Developer's Note)")
                        .setMessage(
                            "To find your Clockface styles, you need to navigate to the lock screen editor manually.(Because the developer couldn't find a way to open it, What a dumb ahh right?)\n\n" +
                                    "1. Go to Settings > Wallpaper and style.\n" +
                                    "2. Tap the 'Lock screen' preview.\n" +
                                    "3. Tap on the clock and go to Styles.\n"+
                                    "4.You can find your various clockfaces there.\n\n"+
                                    "If you already have Lockstar open that module and click on clock this is way faster."
                        )
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                } else {
                    context.startActivity(intent)
                }
            } ?: run {
                Log.w("BadlockLaunch", "No launch intent available for ${module.name}")
                val message = "${module.name} needs to be configured from Samsung Settings"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                openRelevantSettings(context, module.packageName)
            }
        } catch (e: Exception) {
            Log.e("BadlockLaunch", "Failed to launch ${module.name}", e)
            Toast.makeText(context, "Could not launch ${module.name}.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openRelevantSettings(context: Context, packageName: String) {
        val settingsIntent = when (packageName) {
            "com.samsung.android.app.clockface" -> Intent("android.settings.DISPLAY_SETTINGS")
            "com.samsung.systemui.lockstar" -> Intent("android.settings.SECURITY_SETTINGS")
            "com.samsung.android.app.routineplus" -> Intent("android.settings.SETTINGS")
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }
        try {
            context.startActivity(settingsIntent)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Could not open app settings.", Toast.LENGTH_SHORT).show()
        }
    }
}
