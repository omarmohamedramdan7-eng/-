package com.example.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.domain.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Play Policy compliant launcher app discovery using <queries> MAIN/LAUNCHER intent filter,
 * avoiding QUERY_ALL_PACKAGES.
 */
object InstalledAppsProvider {

    suspend fun getLaunchableApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val selfPkg = context.packageName

        resolved
            .mapNotNull { info ->
                val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                val pkg = appInfo.packageName
                if (pkg == selfPkg) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    isSystemApp = isSys
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }
}
