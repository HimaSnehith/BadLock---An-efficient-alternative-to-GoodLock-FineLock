package com.dark.badlock

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.abs
import kotlin.random.Random

import com.dark.badlock.ui.theme.BadlockTheme
import com.dark.badlock.R
import androidx.compose.ui.tooling.preview.Preview

// --- UI THEME & COLORS ---
val DarkBackground = Color(0xFF10121A)
val DarkSurface = Color(0xFF1C1E28)
val PrimaryAccent = Color(0xFF8A2BE2) // Electric Blue-Violet
val GreenAccent = Color(0xFF00FFA3) // Neon Mint
val UpdateYellow = Color(0xFFFFAB00) // Rich Gold
val TextPrimary = Color.White.copy(alpha = 0.9f)
val TextSecondary = Color.White.copy(alpha = 0.7f)

// --- DATA & STATE CLASSES ---
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
    val minAndroidVersion: String?,
    @Transient var launchIntent: Intent?, // Ignored by cache
    val isInstalled: Boolean,
    val isUpdateAvailable: Boolean,
    val category: String,
    val apkMirrorMainPage: String,
    val iconResId: Int?
)

data class AppUpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String?
)

data class VersionFetchResult(
    val version: String? = null,
    val url: String? = null,
    val minAndroidVersion: String? = null
)

sealed interface ModuleState {
    object Loading : ModuleState
    data class Success(val modules: Map<String, List<InstalledModule>>) : ModuleState
    data class Error(val message: String) : ModuleState
}

// --- ON-DISK CACHE MANAGER ---
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
                        launchIntent = getBestLaunchIntent(context, module.packageName, module.name)
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


// --- MODULE DEFINITIONS ---
object GoodLockModules {
    val modules = listOf(
        ModuleInfo("Home Up", "com.samsung.android.app.homestar", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/home-up/"),
        ModuleInfo("LockStar", "com.samsung.systemui.lockstar", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/lockstar/"),
        ModuleInfo("MultiStar", "com.samsung.android.multistar", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/samsung-multistar/"),
        ModuleInfo("QuickStar", "com.samsung.android.qstuner", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/quickstar/"),
        ModuleInfo("NavStar", "com.samsung.systemui.navillera", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/samsung-navstar/"),
        ModuleInfo("SoundAssistant", "com.samsung.android.soundassistant", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/soundassistant/"),
        ModuleInfo("Keys Cafe", "com.samsung.android.keyscafe", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/keys-cafe/"),
        ModuleInfo("Theme Park", "com.samsung.android.themedesigner", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/samsung-theme-park/"),
        ModuleInfo("Nice Shot", "com.samsung.android.app.captureplugin", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/nice-shot/"),
        ModuleInfo("Wonderland", "com.samsung.android.wonderland.wallpaper", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/wonderland/"),
        ModuleInfo("Pentastic", "com.samsung.android.pentastic", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/pentastic/"),
        ModuleInfo("Clockface", "com.samsung.android.app.clockface", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/samsung-clockface/"),
        ModuleInfo("Edge lighting+", "com.samsung.android.edgelightingplus", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/edge-lighting/"),
        ModuleInfo("Edge touch", "com.samsung.android.app.edgetouch", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/edge-touch/"),
        ModuleInfo("Display Assistant", "com.samsung.android.displayassistant", "Make up", "https://www.apkmirror.com/apk/galaxy-labs/display-assistant-beta/"),
        ModuleInfo("Routines+", "com.samsung.android.app.routineplus", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/samsung-routine/"),
        ModuleInfo("NotiStar", "com.samsung.systemui.notilus", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/notistar/"),
        ModuleInfo("RegiStar", "com.samsung.android.app.galaxyregistry", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/registar/"),
        ModuleInfo("Camera Assistant", "com.samsung.android.app.cameraassistant", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/camera-assistant/"),
        ModuleInfo("Nice Catch", "com.samsung.android.app.goodcatch", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/nice-catch/"),
        ModuleInfo("Good Lock", "com.samsung.android.goodlock", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/good-lock-2018/"),
        ModuleInfo("Battery Guardian", "com.samsung.android.statsd", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/battery-guardian/"),
        ModuleInfo("File Guardian", "com.android.samsung.icebox", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/file-guardian/"),
        ModuleInfo("Memory Guardian", "com.samsung.android.memoryguardian", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/memory-guardian/"),
        ModuleInfo("App Booster", "com.samsung.android.appbooster", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/app-booster/"),
        ModuleInfo("Thermal Guardian", "com.samsung.android.thermalguardian", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/thermal-guardian/"),
        ModuleInfo("Media File Guardian", "com.samsung.android.mediaguardian", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/media-file-guardian/"),
        ModuleInfo("One Hand Operation+", "com.samsung.android.sidegesturepad", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/one-hand-operation/"),
        ModuleInfo("Gallery Assistant", "com.samsung.android.gallery.assistant.app", "Make up", "https://www.apkmirror.com/apk/samsung-electronics/gallery-assistant/"),
        ModuleInfo("Battery Tracker", "com.android.samsung.batteryusage", "Life up", "https://www.apkmirror.com/apk/samsung-electronics/battery-tracker/")
    )
}

// --- Helper functions for scraping ---
private val browserUserAgent = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"

private fun createJsoupConnection(url: String) = Jsoup.connect(url)
    .userAgent(browserUserAgent)
    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
    .header("Accept-Language", "en-US,en;q=0.9")
    .header("Cache-Control", "no-cache")
    .header("Connection", "keep-alive")
    .header("Sec-Ch-Ua", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
    .header("Sec-Ch-Ua-Mobile", "?1")
    .header("Sec-Ch-Ua-Platform", "\"Android\"")
    .header("Sec-Fetch-Dest", "document")
    .header("Sec-Fetch-Mode", "navigate")
    .header("Sec-Fetch-Site", "none")
    .header("Sec-Fetch-User", "?1")
    .header("Upgrade-Insecure-Requests", "1")
    .timeout(30000)
    .followRedirects(true)

private fun cleanVersionText(rawText: String): String {
    var cleaned = rawText.trim()
    cleaned = cleaned.replace("""(?i)(version|api|level|sdk)""".toRegex(), "").trim()
    if (cleaned.matches("""\d+\+?""".toRegex())) {
        val number = cleaned.replace("+", "")
        return if (cleaned.contains("+")) "Android $number+" else "Android $number"
    }
    if (cleaned.matches("""(?i)android\s*\d+\+?""".toRegex())) {
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    if (cleaned.matches("""\d+(?:\.\d+)*""".toRegex())) {
        return "Android $cleaned"
    }
    if (cleaned.matches("""\d{2,}""".toRegex())) {
        return "API $cleaned"
    }
    return cleaned.ifEmpty { null } ?: "Unknown"
}

private fun scrapeMinVersion(doc: Document): String? {
    // Strategy 1: Enhanced table parsing with flexible selectors
    try {
        val possibleTables = doc.select("div[class*=table], table, div.downloadBox")
        for (table in possibleTables) {
            val rows = table.select("div[class*=row], tr, div[class*=variant]")
            var minVersionIndex = -1
            var headerRow: Element? = null

            for (row in rows) {
                val cells = row.select("div[class*=cell], td, th, div[class*=col]")
                for (index in cells.indices) {
                    val cell = cells[index]
                    val cellText = cell.text().lowercase().trim()
                    if (cellText.contains("minimum") || cellText.contains("min") ||
                        cellText.contains("requires") || cellText.contains("android")) {
                        minVersionIndex = index
                        headerRow = row
                        break
                    }
                }
                if (minVersionIndex != -1) break
            }

            if (minVersionIndex != -1 && headerRow != null) {
                val headerIndex = rows.indexOf(headerRow)
                for (i in (headerIndex + 1) until rows.size) {
                    val dataRow = rows[i]
                    val dataCells = dataRow.select("div[class*=cell], td, div[class*=col]")
                    if (dataCells.size > minVersionIndex) {
                        val versionText = dataCells[minVersionIndex].text().trim()
                        if (versionText.isNotEmpty() &&
                            !versionText.lowercase().contains("minimum") &&
                            (versionText.contains("android", ignoreCase = true) ||
                                    versionText.matches(""".*\d+.*""".toRegex()))) {
                            return cleanVersionText(versionText)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w("BadlockScrape", "Table parsing failed: ${e.message}")
    }

    // Strategy 2: Enhanced appspec-row with flexible matching
    try {
        val rows = doc.select("div[class*=appspec], div[class*=spec], div[class*=info-row]")
        for (row in rows) {
            val titleElements = row.select("div[class*=title], div[class*=label], span[class*=label]")
            val valueElements = row.select("div[class*=value], div[class*=content]")

            if (titleElements.isNotEmpty() && valueElements.isNotEmpty()) {
                val title = titleElements.first()!!.text().lowercase().trim()
                if (title.contains("minimum") || title.contains("requires") || title.contains("android")) {
                    val value = valueElements.first()!!.text().trim()
                    if (value.isNotEmpty() && (value.contains("android", ignoreCase = true) ||
                                value.matches(""".*\d+.*""".toRegex()))) {
                        return cleanVersionText(value)
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w("BadlockScrape", "Appspec parsing failed: ${e.message}")
    }

    return null
}

// --- VERSION FETCHING FUNCTIONS ---
suspend fun checkAppUpdate(): AppUpdateInfo? {
    return withContext(Dispatchers.IO) {
        try {
            val repoUrl = "https://api.github.com/repos/Dark-254/BadLock---An-efficient-alternative-to-GoodLock-FineLock/releases/latest"
            val connection = Jsoup.connect(repoUrl)
                .ignoreContentType(true)
                .userAgent("Badlock-Update-Checker")
                .timeout(10000)
                .execute()
            
            val json = connection.body()
            val gson = Gson()
            val releaseMap = gson.fromJson(json, Map::class.java) as Map<String, Any>
            
            val tagName = releaseMap["tag_name"] as? String ?: return@withContext null
            val htmlUrl = releaseMap["html_url"] as? String ?: return@withContext null
            val body = releaseMap["body"] as? String
            
            val latestVer = tagName.lowercase().replace("v", "").trim()
            
            AppUpdateInfo(
                latestVersion = latestVer,
                downloadUrl = htmlUrl,
                releaseNotes = body
            )
        } catch (e: Exception) {
            Log.e("BadlockUpdate", "Failed to check for app updates", e)
            null
        }
    }
}

suspend fun fetchLatestVersionFromRssFeed(url: String): VersionFetchResult {
    val feedUrl = if (url.endsWith("/")) "${url}feed/" else "$url/feed/"
    return withContext(Dispatchers.IO) {
        try {
            val doc = createJsoupConnection(feedUrl).get()
            val firstItem = doc.selectFirst("item") ?: return@withContext VersionFetchResult()

            val title = firstItem.selectFirst("title")?.text() ?: ""
            val link = firstItem.selectFirst("link")?.text()

            val regex = """(\d+(\.\d+)+)""".toRegex()
            val version = regex.find(title)?.value?.trim()

            var minAndroidVersion: String? = null
            if (link != null) {
                try {
                    val versionDoc = createJsoupConnection(link).get()
                    minAndroidVersion = scrapeMinVersion(versionDoc)
                } catch (e: Exception) {
                    Log.w("BadlockFetch", "Could not fetch min version from $link", e)
                }
            }

            VersionFetchResult(version = version, url = link, minAndroidVersion = minAndroidVersion)
        } catch (e: Exception) {
            Log.e("BadlockFetch", "RSS fetch failed for $url", e)
            throw e
        }
    }
}

suspend fun fetchLatestVersionFromHtmlFallback(url: String): VersionFetchResult {
    return withContext(Dispatchers.IO) {
        try {
            val mainDoc = createJsoupConnection(url).get()
            // Identify all version links in the primary content area
            val versionElements = mainDoc.select("#primary div.list-row a.fontBlack")
            
            if (versionElements.isEmpty()) {
                Log.w("BadlockFetch", "No version links found for $url")
                return@withContext VersionFetchResult()
            }

            val regex = """(\d+(\.\d+)+)""".toRegex()
            
            // Collect versions from the first 10 entries to find the actual highest version
            val foundVersions = versionElements.take(10).mapNotNull { element ->
                val title = element.text()
                val ver = regex.find(title)?.value?.trim()
                if (ver != null) {
                    Pair(ver, "https://www.apkmirror.com" + element.attr("href"))
                } else null
            }

            if (foundVersions.isEmpty()) return@withContext VersionFetchResult()

            // Sort by version components to find the true latest
            val latestEntry = foundVersions.maxByOrNull { (ver, _) ->
                ver.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }.let { parts ->
                    // Pad with zeros to handle different lengths correctly
                    List(6) { i -> parts.getOrElse(i) { 0 } }.joinToString(",") { it.toString().padStart(5, '0') }
                }
            } ?: foundVersions[0]

            val (version, latestVersionPageUrl) = latestEntry
            var minAndroidVersion: String? = null

            try {
                // Secondary fetch for precise version and min android version
                val versionDoc = createJsoupConnection(latestVersionPageUrl).get()
                val preciseVersion = versionDoc.selectFirst(".appspec-value")?.text()?.trim()?.split(" ")?.first()
                // If a precise version exists on the specific page, we trust it over the title text
                if (!preciseVersion.isNullOrEmpty() && regex.matches(preciseVersion)) {
                    // We found a specific version, keeping it as the result
                }
                minAndroidVersion = scrapeMinVersion(versionDoc)
            } catch (e: Exception) {
                Log.w("BadlockFetch", "Could not fetch details from $latestVersionPageUrl", e)
            }

            VersionFetchResult(version = version, url = latestVersionPageUrl, minAndroidVersion = minAndroidVersion)
        } catch (e: Exception) {
            Log.e("BadlockFetch", "FAIL: HTML Fallback. An error occurred for URL: $url", e)
            VersionFetchResult()
        }
    }
}


fun isUpdateAvailable(moduleName: String, installedVersion: String?, latestVersion: String?): Boolean {
    if (installedVersion.isNullOrEmpty() || latestVersion.isNullOrEmpty()) return false
    try {
        val installedParts = installedVersion.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val latestParts = latestVersion.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val maxParts = maxOf(installedParts.size, latestParts.size)
        for (i in 0 until maxParts) {
            val installed = installedParts.getOrElse(i) { 0 }
            val latest = latestParts.getOrElse(i) { 0 }
            if (latest > installed) return true
            if (latest < installed) return false
        }
    } catch (e: Exception) {
        return false
    }
    return false
}

// --- ENHANCED LAUNCH INTENT FUNCTIONS ---
fun getSpecialLaunchIntent(context: Context, packageName: String, moduleName: String): Intent? {
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

fun isProblematicLauncherIntent(packageName: String, intent: Intent): Boolean {
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
fun isProblematicActivity(packageName: String, activityName: String): Boolean {
    val problematicKeywords = listOf("shortcut", "widget", "credit", "about", "help")
    return problematicKeywords.any { keyword ->
        activityName.contains(keyword, ignoreCase = true)
    }
}

fun findWorkingActivity(context: Context, packageName: String, activityNames: List<String>): Intent? {
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

fun findBestActivityDeepSearch(context: Context, packageName: String, moduleName: String): Intent? {
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

fun calculateActivityScore(activityName: String, moduleName: String): Int {
    var score = 0
    val goodKeywords = listOf("main" to 50, "home" to 40, "launcher" to 35, "settings" to 30, "ui" to 25, moduleName.lowercase().replace(" ", "") to 45)
    val badKeywords = listOf("shortcut" to -100, "widget" to -80, "credit" to -90, "about" to -70, "help" to -60, "splash" to -40, "intro" to -40)
    val lowerActivityName = activityName.lowercase()
    goodKeywords.forEach { (keyword, points) -> if (lowerActivityName.contains(keyword)) score += points }
    badKeywords.forEach { (keyword, points) -> if (lowerActivityName.contains(keyword)) score += points }
    if (lowerActivityName.endsWith("activity")) score += 10
    return score
}

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

// --- DATA LOADING FUNCTION ---
suspend fun loadData(context: Context, cacheManager: CacheManager): ModuleState {
    val packageManager = context.packageManager
    val semaphore = Semaphore(3) // Limit concurrent network requests to avoid being blocked
    return withContext(Dispatchers.IO) {
        try {
            val allModules = coroutineScope {
                GoodLockModules.modules.map { moduleInfo ->
                    async {
                        semaphore.withPermit {
                            // Add a random delay to mimic human behavior and avoid rate limits
                            delay(Random.nextLong(300, 1000))

                            val isInstalled = try {
                                packageManager.getPackageInfo(moduleInfo.packageName, 0); true
                            } catch (e: Exception) { false }

                            var versionResult = VersionFetchResult()
                            var installedVersion: String? = null
                            var launchIntent: Intent? = null

                            if (isInstalled) {
                                try {
                                    val pkgInfo = packageManager.getPackageInfo(moduleInfo.packageName, 0)
                                    installedVersion = pkgInfo.versionName
                                    launchIntent = getBestLaunchIntent(context, moduleInfo.packageName, moduleInfo.name)
                                } catch (e: Exception) {
                                    Log.e("BadlockLoad", "Error getting package info for ${moduleInfo.packageName}", e)
                                }

                                // Prioritize HTML fallback over RSS as RSS is frequently blocked by Cloudflare
                                versionResult = fetchLatestVersionFromHtmlFallback(moduleInfo.apkMirrorMainPage)
                                
                                if (versionResult.version == null) {
                                    try {
                                        versionResult = fetchLatestVersionFromRssFeed(moduleInfo.apkMirrorMainPage)
                                    } catch (e: Exception) {
                                        Log.w("BadlockFetch", "RSS also failed for ${moduleInfo.name}", e)
                                    }
                                }
                            }

                            val resourceName = moduleInfo.name.lowercase().replace(" ", "_").replace("+", "")
                            val iconResId = context.resources.getIdentifier(resourceName, "drawable", context.packageName).let { if (it == 0) null else it }
                            val updateAvailable = isUpdateAvailable(moduleInfo.name, installedVersion, versionResult.version)

                            InstalledModule(
                                name = moduleInfo.name,
                                packageName = moduleInfo.packageName,
                                versionName = installedVersion,
                                latestVersion = versionResult.version,
                                latestVersionUrl = versionResult.url,
                                minAndroidVersion = versionResult.minAndroidVersion,
                                launchIntent = launchIntent,
                                isInstalled = isInstalled,
                                isUpdateAvailable = updateAvailable,
                                category = moduleInfo.category,
                                apkMirrorMainPage = moduleInfo.apkMirrorMainPage,
                                iconResId = iconResId
                            )
                        }
                    }
                }.awaitAll()
            }

            val groupedAndSorted = allModules.groupBy { it.category }
                .mapValues { entry ->
                    entry.value.sortedWith(
                        compareByDescending<InstalledModule> { it.isUpdateAvailable }
                            .thenByDescending { it.isInstalled }
                            .thenBy { it.name }
                    )
                }
            val successState = ModuleState.Success(groupedAndSorted)
            cacheManager.save(successState)
            successState
        } catch (e: Exception) {
            when(e) {
                is UnknownHostException, is SocketTimeoutException -> ModuleState.Error("Could not connect to server. Please check your internet connection.")
                else -> {
                    Log.e("BadlockLoad", "An unexpected error occurred during data load", e)
                    ModuleState.Error("An unexpected error occurred.")
                }
            }
        }
    }
}

// --- UTILITY FUNCTIONS ---
fun openRelevantSettings(context: Context, packageName: String) {
    val settingsIntent = when (packageName) {
        "com.samsung.android.app.clockface" -> Intent("android.settings.DISPLAY_SETTINGS")
        "com.samsung.systemui.lockstar" -> Intent("android.settings.SECURITY_SETTINGS")
        "com.samsung.android.app.routineplus" -> Intent("android.settings.SETTINGS")
        else -> Intent("android.settings.APPLICATION_DETAILS_SETTINGS").apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }
    try {
        context.startActivity(settingsIntent)
    } catch (e: Exception) {
        context.startActivity(Intent("android.settings.SETTINGS"))
    }
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

// --- MAIN ACTIVITY & UI ---
class MainActivity : ComponentActivity() {
    private lateinit var cacheManager: CacheManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cacheManager = CacheManager(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()
        setContent {
            BadlockTheme {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        window.statusBarColor = Color.Transparent.toArgb()
                        window.navigationBarColor = Color.Transparent.toArgb()
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    MainScreen(cacheManager)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(cacheManager: CacheManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var moduleState by remember { mutableStateOf<ModuleState>(cacheManager.load(context) ?: ModuleState.Loading) }
    var appUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    fun refreshData(force: Boolean = false) {
        if (cacheManager.load(context) == null || force) {
            moduleState = ModuleState.Loading
        }
        coroutineScope.launch {
            // Check for app's own update
            launch {
                val update = checkAppUpdate()
                val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0"
                
                // version parts comparison logic
                fun isUpdateAvailable(current: String, latest: String): Boolean {
                    try {
                        val cParts = current.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
                        val lParts = latest.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
                        for (i in 0 until maxOf(cParts.size, lParts.size)) {
                            if (lParts.getOrElse(i) { 0 } > cParts.getOrElse(i) { 0 }) return true
                            if (lParts.getOrElse(i) { 0 } < cParts.getOrElse(i) { 0 }) return false
                        }
                    } catch (e: Exception) { /* ignore */ }
                    return false
                }

                if (update != null && isUpdateAvailable(currentVersion, update.latestVersion)) {
                    appUpdateInfo = update
                    showUpdateDialog = true
                }
            }

            val newState = loadData(context, cacheManager)
            if (newState is ModuleState.Success) {
                moduleState = newState
            } else if (newState is ModuleState.Error && cacheManager.load(context) == null) {
                moduleState = newState
            } else {
                Toast.makeText(context, "Update check failed, showing last known data.", Toast.LENGTH_SHORT).show()
                moduleState = cacheManager.load(context)!!
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val lastRefreshTime = cacheManager.getLastRefreshTime()
                val currentTime = System.currentTimeMillis()
                if (lastRefreshTime == 0L || (currentTime - lastRefreshTime) > 3 * 24 * 60 * 60 * 1000L) {
                    refreshData()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("BadlockAutoRefresh", "Package change detected, refreshing data...")
                refreshData(force = true)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)
        onDispose {
            context.unregisterReceiver(packageReceiver)
        }
    }

    val onModuleClick = remember<(InstalledModule) -> Unit> {
        { module -> if (module.isInstalled) launchModule(context, module) else openUrl(context, module.apkMirrorMainPage) }
    }
    val onWebsiteClick = remember<(String) -> Unit> {
        { url -> openUrl(context, url) }
    }
    val onUpdateClick = remember<(InstalledModule) -> Unit> {
        { module -> module.latestVersionUrl?.let { openUrl(context, it) } }
    }
    val onAppInfoClick = remember<(String) -> Unit> {
        { packageName -> openAppInfo(context, packageName) }
    }

    if (showUpdateDialog && appUpdateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = PrimaryAccent)
                    Spacer(Modifier.width(12.dp))
                    Text("Badlock Update", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("A new version of Badlock is available!", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("Version: v${appUpdateInfo!!.latestVersion}", color = UpdateYellow, fontWeight = FontWeight.Bold)
                    if (!appUpdateInfo!!.releaseNotes.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What's new:", fontSize = 12.sp, color = TextPrimary)
                        Text(
                            appUpdateInfo!!.releaseNotes!!,
                            fontSize = 11.sp,
                            maxLines = 5,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        openUrl(context, appUpdateInfo!!.downloadUrl)
                        showUpdateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Go to GitHub", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later", color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        when (val state = moduleState) {
            is ModuleState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryAccent, strokeWidth = 3.dp)
                }
            }
            is ModuleState.Error -> {
                ErrorScreen(errorMessage = state.message, onRetry = { refreshData(force = true) })
            }
            is ModuleState.Success -> {
                val updatableModules = remember(state.modules) {
                    state.modules.values.flatten().filter { it.isUpdateAvailable }
                }
                val tabs = listOf("Make up", "Life up", "Updates")
                val pagerState = rememberPagerState(pageCount = { tabs.size })

                Column(modifier = Modifier.fillMaxSize()) {
                    LargeHeader(
                        title = "Badlock",
                        subtitle = if (updatableModules.isNotEmpty()) "${updatableModules.size} updates available" else "Your modules are up to date",
                        onRefresh = { refreshData(force = true) },
                        refreshEnabled = moduleState != ModuleState.Loading
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondBoundsPageCount = 1,
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            val alpha = 1f - abs(pageOffset).coerceIn(0f, 1f)
                            val scale = 0.95f + (0.05f * (1f - abs(pageOffset).coerceIn(0f, 1f)))

                            Box(modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    this.alpha = alpha
                                    this.scaleX = scale
                                    this.scaleY = scale
                                }
                            ) {
                                val pageTitle = tabs[page]
                                val modulesToShow = when (pageTitle) {
                                    "Updates" -> updatableModules
                                    else -> state.modules[pageTitle] ?: emptyList()
                                }
                                ModuleList(
                                    modules = modulesToShow,
                                    showEmptyMessage = (pageTitle == "Updates"),
                                    onModuleClick = onModuleClick,
                                    onWebsiteClick = onWebsiteClick,
                                    onUpdateClick = onUpdateClick,
                                    onAppInfoClick = onAppInfoClick
                                )
                            }
                        }

                        // Top Fade Effect Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(DarkBackground, Color.Transparent)
                                    )
                                )
                        )
                    }
                }

                // Bottom Fade Effect
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, DarkBackground.copy(alpha = 0.5f), DarkBackground.copy(alpha = 0.9f))
                            )
                        )
                )

                // Bottom Island Navigation
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                ) {
                    BottomIsland(
                        tabs = tabs,
                        currentPage = pagerState.currentPage,
                        updatableCount = updatableModules.size,
                        onTabClick = { index ->
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LargeHeader(
    title: String,
    subtitle: String,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = (-1).sp
            )
            Surface(
                onClick = onRefresh,
                enabled = refreshEnabled,
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 16.sp,
            color = if (subtitle.contains("updates")) UpdateYellow else TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomIsland(
    tabs: List<String>,
    currentPage: Int,
    updatableCount: Int,
    onTabClick: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(340.dp)
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = DarkSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        tonalElevation = 12.dp,
        shadowElevation = 20.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = currentPage == index
                val contentColor by animateColorAsState(
                    if (isSelected) PrimaryAccent else TextSecondary,
                    label = "contentColor"
                )
                val backgroundColor by animateColorAsState(
                    if (isSelected) PrimaryAccent.copy(alpha = 0.12f) else Color.Transparent,
                    label = "backgroundColor"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.25f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "iconScale"
                )

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(backgroundColor)
                        .clickable { onTabClick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                    ) {
                        val icon = when (title) {
                            "Updates" -> Icons.Default.SystemUpdate
                            "Make up" -> Icons.Default.Palette
                            else -> Icons.Default.Style
                        }
                        
                        if (title == "Updates" && updatableCount > 0) {
                            BadgedBox(
                                badge = { 
                                    Badge(
                                        containerColor = PrimaryAccent,
                                        contentColor = Color.White,
                                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                                    ) { Text("$updatableCount", fontSize = 10.sp) } 
                                }
                            ) {
                                Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleList(
    modules: List<InstalledModule>,
    showEmptyMessage: Boolean = false,
    onModuleClick: (InstalledModule) -> Unit,
    onWebsiteClick: (String) -> Unit,
    onUpdateClick: (InstalledModule) -> Unit,
    onAppInfoClick: (String) -> Unit
) {
    if (modules.isEmpty() && showEmptyMessage) {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(30.dp),
                color = DarkSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.DoneAll, contentDescription = "All up to date", tint = GreenAccent, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("All Clear!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("All your modules are up-to-date.", color = TextSecondary, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items = modules, key = { it.packageName }) { module ->
                ModuleCard(
                    module = module,
                    onModuleClick = { onModuleClick(module) },
                    onWebsiteClick = { onWebsiteClick(module.apkMirrorMainPage) },
                    onUpdateClick = { onUpdateClick(module) },
                    onAppInfoClick = { onAppInfoClick(module.packageName) }
                )
            }
        }
    }
}

@Composable
fun ModuleCard(
    module: InstalledModule,
    onModuleClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onAppInfoClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onModuleClick),
        color = DarkSurface.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = DarkBackground
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = module.iconResId?.let { painterResource(id = it) }
                            ?: painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "${module.name} icon",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(14.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                VersionInfo(module)
            }
            
            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!module.isInstalled) {
                    Button(
                        onClick = onWebsiteClick,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Install", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                } else {
                    if (module.isUpdateAvailable) {
                        Button(
                            onClick = onUpdateClick,
                            colors = ButtonDefaults.buttonColors(containerColor = UpdateYellow, contentColor = Color.Black),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Update", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    
                    IconButton(
                        onClick = onWebsiteClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Website",
                            tint = TextSecondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = onAppInfoClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "App Info",
                            tint = TextSecondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VersionInfo(module: InstalledModule) {
    val versionText = if (module.isInstalled) "v${module.versionName ?: "N/A"}" else "Tap to install"
    Text(versionText, color = TextSecondary, fontSize = 12.sp, maxLines = 1)

    if (module.latestVersion != null && module.isInstalled) {
        val color = if (module.isUpdateAvailable) UpdateYellow else TextSecondary
        Text("Latest: v${module.latestVersion}", color = color.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
    }

    if (module.latestVersion == null && module.isInstalled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("Version check failed", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun ErrorScreen(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = DarkSurface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.WifiOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Connection Issue", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(errorMessage, color = TextSecondary, textAlign = TextAlign.Center, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(56.dp).fillMaxWidth(0.7f)
        ) {
            Text("Try Again", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    BadlockTheme {
        Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
            // Mocking the success state
            val mockModules = listOf(
                InstalledModule(
                    name = "Home Up",
                    packageName = "com.samsung.android.app.homestar",
                    versionName = "15.0.01.19",
                    latestVersion = "16.0.00.90",
                    latestVersionUrl = "https://www.apkmirror.com",
                    minAndroidVersion = null,
                    launchIntent = null,
                    isInstalled = true,
                    isUpdateAvailable = true,
                    category = "Make up",
                    apkMirrorMainPage = "https://www.apkmirror.com",
                    iconResId = null
                ),
                InstalledModule(
                    name = "Nice Shot",
                    packageName = "com.samsung.android.app.captureplugin",
                    versionName = "2.3.09",
                    latestVersion = "2.3.09",
                    latestVersionUrl = "https://www.apkmirror.com",
                    minAndroidVersion = null,
                    launchIntent = null,
                    isInstalled = true,
                    isUpdateAvailable = false,
                    category = "Make up",
                    apkMirrorMainPage = "https://www.apkmirror.com",
                    iconResId = null
                )
            )
            
            Column(modifier = Modifier.fillMaxSize()) {
                LargeHeader(
                    title = "Badlock",
                    subtitle = "1 update available",
                    onRefresh = {},
                    refreshEnabled = true
                )
                ModuleList(
                    modules = mockModules,
                    onModuleClick = {},
                    onWebsiteClick = {},
                    onUpdateClick = {},
                    onAppInfoClick = {}
                )
            }
            
            // Render the dialog content directly in the Box to avoid ClassNotFoundException in preview
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.padding(24.dp).widthIn(max = 400.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = DarkSurface,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = PrimaryAccent)
                            Spacer(Modifier.width(12.dp))
                            Text("Badlock Update", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text("A new version of Badlock is available!", fontWeight = FontWeight.Medium, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("Version: v2.6", color = UpdateYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        
                        Spacer(Modifier.height(16.dp))
                        Text("What's new:", fontSize = 12.sp, color = TextPrimary)
                        Text(
                            "- Optimized update checker\n- Added manual website button\n- Bug fixes for One UI 7",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState())
                        )
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { }) {
                                Text("Later", color = TextSecondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                            ) {
                                Text("Go to GitHub", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
