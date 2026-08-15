package com.dark.badlock.logic

import android.os.Build
import android.util.Log
import com.dark.badlock.data.AppUpdateInfo
import com.dark.badlock.data.VersionFetchResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object UpdateChecker {

    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"

    private fun createJsoupConnection(url: String) = Jsoup.connect(url)
        .userAgent(BROWSER_USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Cache-Control", "no-cache")
        .header("Connection", "keep-alive")
        .header("Sec-Ch-Ua", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
        .header("Sec-Ch-Ua-Mobile", "?1")
        .header("Sec-Ch-Ua-Platform", "\"Android\"")

    fun isUpdateAvailable(current: String?, latest: String?): Boolean {
        if (current.isNullOrEmpty() || latest.isNullOrEmpty()) return false
        try {
            val cParts = current.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
            val lParts = latest.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
            for (i in 0 until maxOf(cParts.size, lParts.size)) {
                val lVal = lParts.getOrElse(i) { 0 }
                val cVal = cParts.getOrElse(i) { 0 }
                if (lVal > cVal) return true
                if (lVal < cVal) return false
            }
        } catch (e: Exception) { /* ignore */ }
        return false
    }

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
                var variantUrl: String? = null
                if (link != null) {
                    try {
                        val versionDoc = createJsoupConnection(link).get()
                        minAndroidVersion = scrapeMinVersion(versionDoc)
                        variantUrl = scrapeVariantUrl(versionDoc, getDeviceArchitecture())
                    } catch (e: Exception) {
                        Log.w("BadlockFetch", "Could not fetch details from $link", e)
                    }
                }

                VersionFetchResult(
                    version = version, 
                    url = link, 
                    variantUrl = variantUrl,
                    minAndroidVersion = minAndroidVersion
                )
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
                // Identify all version links - APKMirror uses a specific layout for versions
                // We target links that are likely to be version rows and contain "APK" (ignoring Bundles for now as they are harder to parse/install)
                val versionElements = mainDoc.select("#primary div.list-row a.fontBlack")
                    .filter { it.text().contains("APK", ignoreCase = true) }

                if (versionElements.isEmpty()) {
                    Log.w("BadlockFetch", "No valid APK version links found for $url")
                    return@withContext VersionFetchResult()
                }

                val regex = """(\d+(\.\d+)+)""".toRegex()

                val foundVersions = versionElements.take(10).mapNotNull { element ->
                    val title = element.text()
                    val ver = regex.find(title)?.value?.trim()
                    if (ver != null) {
                        Pair(ver, "https://www.apkmirror.com" + element.attr("href"))
                    } else null
                }

                if (foundVersions.isEmpty()) return@withContext VersionFetchResult()

                val latestEntry = foundVersions.maxByOrNull { (ver, _) ->
                    ver.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }.let { parts ->
                        List(6) { i -> parts.getOrElse(i) { 0 } }.joinToString(",") { it.toString().padStart(5, '0') }
                    }
                } ?: foundVersions[0]

                val (version, latestVersionPageUrl) = latestEntry
                var minAndroidVersion: String? = null
                var variantUrl: String? = null

                try {
                    // Secondary fetch for precise version and min android version
                    val versionDoc = createJsoupConnection(latestVersionPageUrl).get()
                    minAndroidVersion = scrapeMinVersion(versionDoc)
                    
                    // Attempt to find the specific variant for our architecture
                    variantUrl = scrapeVariantUrl(versionDoc, getDeviceArchitecture())
                } catch (e: Exception) {
                    Log.w("BadlockFetch", "Could not fetch details from $latestVersionPageUrl", e)
                }

                VersionFetchResult(
                    version = version, 
                    url = latestVersionPageUrl, 
                    variantUrl = variantUrl,
                    minAndroidVersion = minAndroidVersion
                )
            } catch (e: Exception) {
                Log.e("BadlockFetch", "FAIL: HTML Fallback. An error occurred for URL: $url", e)
                VersionFetchResult()
            }
        }
    }

    private fun scrapeVariantUrl(doc: Document, preferredArch: String): String? {
        try {
            // APKMirror variant tables are usually inside div.variants-table or similar
            val rows = doc.select(".variants-table .table-row, table tr").drop(1) // Drop header
            
            var bestLink: String? = null
            var fallbackLink: String? = null

            for (row in rows) {
                val cells = row.select(".table-cell, td")
                if (cells.size < 2) continue

                val variantLinkElement = cells.first()?.selectFirst("a") ?: continue
                val variantLink = "https://www.apkmirror.com" + variantLinkElement.attr("href")
                val archText = cells.joinToString(" ") { it.text() }.lowercase()

                if (archText.contains(preferredArch.lowercase())) {
                    return variantLink // Exact match found
                }
                
                if (archText.contains("universal") || archText.contains("no arch")) {
                    fallbackLink = variantLink
                }
                
                // If we haven't found anything yet, take the first one as last resort
                if (bestLink == null) bestLink = variantLink
            }
            
            return fallbackLink ?: bestLink
        } catch (e: Exception) {
            Log.e("BadlockFetch", "Error scraping variant URL", e)
        }
        return null
    }

    private fun scrapeMinVersion(doc: Document): String? {
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
                            val versionText = dataCells[minVersionIndex].text()
                            val cleaned = cleanVersionText(versionText)
                            if (cleaned.isNotEmpty()) return cleaned
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BadlockFetch", "Error scraping min version", e)
        }
        return null
    }

    fun getDeviceArchitecture(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.contains("arm64") -> "arm64-v8a"
            abi.contains("v7") -> "armeabi-v7a"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "x86"
            else -> abi
        }
    }

    private fun cleanVersionText(text: String): String {
        val regex = """(\d+(\.\d+)*)""".toRegex()
        val match = regex.find(text)
        return match?.value ?: ""
    }
}
