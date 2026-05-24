package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

object IconPackManager {
    private const val TAG = "IconPackManager"
    private var cachedIconPack: String? = null
    private val iconPackMapping = HashMap<String, String>()

    fun getSelectedIconPack(context: Context): String {
        val prefs = context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
        return prefs.getString("selected_icon_pack", "default") ?: "default"
    }

    fun setSelectedIconPack(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_icon_pack", packageName).apply()
        // Clear mapping cache so that it reloads on next use
        clearCache()
    }

    fun clearCache() {
        cachedIconPack = null
        iconPackMapping.clear()
        // Also clear DragResizeOverlay icon cache to force reload
        DragResizeOverlay.clearIconCache()
    }

    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacks = mutableListOf<IconPackInfo>()
        
        // Add default system launcher icons option
        iconPacks.add(IconPackInfo("System Default", "default", null))
        
        val intents = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent("com.gau.go.launcherex.theme"),
            Intent("com.fede.launcher.THEME_ICONPACK")
        )
        
        val seenPackages = mutableSetOf<String>()
        
        for (intent in intents) {
            try {
                val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                for (info in resolveInfos) {
                    val packageName = info.activityInfo.packageName
                    if (seenPackages.add(packageName)) {
                        val label = info.loadLabel(pm).toString()
                        val icon = info.loadIcon(pm)
                        iconPacks.add(IconPackInfo(label, packageName, icon))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying icon packs for intent ${intent.action}", e)
            }
        }
        return iconPacks
    }

    private fun getAppFilterParser(context: Context, iconPackPackage: String): XmlPullParser? {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(iconPackPackage, 0)
            val iconPackRes = pm.getResourcesForApplication(appInfo)
            
            // Try assets/appfilter.xml first
            try {
                val stream = iconPackRes.assets.open("appfilter.xml")
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(stream, "UTF-8")
                parser
            } catch (e: Exception) {
                // Try res/xml/appfilter.xml
                val resId = iconPackRes.getIdentifier("appfilter", "xml", iconPackPackage)
                if (resId != 0) {
                    iconPackRes.getXml(resId)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get appfilter parser for package $iconPackPackage", e)
            null
        }
    }

    private fun loadIconPackMapping(context: Context, iconPackPackage: String) {
        if (cachedIconPack == iconPackPackage && iconPackMapping.isNotEmpty()) {
            return
        }
        iconPackMapping.clear()
        cachedIconPack = iconPackPackage
        
        val parser = getAppFilterParser(context, iconPackPackage) ?: return
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawableName = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawableName != null) {
                        if (component.startsWith("ComponentInfo{") && component.endsWith("}")) {
                            val compInfo = component.substring("ComponentInfo{".length, component.length - 1)
                            val slashIndex = compInfo.indexOf('/')
                            if (slashIndex != -1) {
                                val pkg = compInfo.substring(0, slashIndex).trim()
                                val cls = compInfo.substring(slashIndex + 1).trim()
                                iconPackMapping[pkg] = drawableName
                                iconPackMapping["$pkg/$cls"] = drawableName
                            } else {
                                iconPackMapping[compInfo.trim()] = drawableName
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            Log.d(TAG, "Loaded ${iconPackMapping.size} icon mappings for pack $iconPackPackage")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing appfilter.xml", e)
        }
    }

    fun getIconFromPack(context: Context, appPackageName: String, launcherActivityName: String? = null): Drawable? {
        val pm = context.packageManager
        val iconPackPackage = getSelectedIconPack(context)
        if (iconPackPackage.isEmpty() || iconPackPackage == "default") return null
        
        try {
            loadIconPackMapping(context, iconPackPackage)
            
            // 1. Try mapping by full component (pkg/activity)
            var drawableName = if (launcherActivityName != null) {
                iconPackMapping["$appPackageName/$launcherActivityName"]
            } else null
            
            // 2. Try mapping by package name only
            if (drawableName == null) {
                drawableName = iconPackMapping[appPackageName]
            }
            
            // 3. Fallback: try drawable resource named after package name with underscores
            if (drawableName == null) {
                drawableName = appPackageName.replace('.', '_')
            }
            
            if (drawableName != null) {
                val appInfo = pm.getApplicationInfo(iconPackPackage, 0)
                val iconPackRes = pm.getResourcesForApplication(appInfo)
                val resId = iconPackRes.getIdentifier(drawableName, "drawable", iconPackPackage)
                if (resId != 0) {
                    return androidx.core.content.res.ResourcesCompat.getDrawable(iconPackRes, resId, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting icon from pack", e)
        }
        return null
    }
}
