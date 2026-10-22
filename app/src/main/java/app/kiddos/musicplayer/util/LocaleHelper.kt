package app.kiddos.musicplayer.util

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

class LocalizedContextWrapper(
    base: Context,
    private val localizedContext: Context
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedContext.resources
    override fun getAssets(): AssetManager = localizedContext.assets
}

object LocaleHelper {
    const val PREFS_NAME = "music_player_prefs"
    const val KEY_LANGUAGE = "selected_language"

    const val LANG_SYSTEM = ""
    const val LANG_EN = "en"
    const val LANG_ZH_TW = "zh-TW"

    val supportedLanguages = listOf(
        LANG_SYSTEM,
        LANG_EN,
        LANG_ZH_TW
    )

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (localeManager != null) {
                    if (languageCode.isEmpty()) {
                        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
                    } else {
                        localeManager.applicationLocales = LocaleList.forLanguageTags(languageCode)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getLocale(languageCode: String): Locale? {
        return when {
            languageCode.equals(LANG_EN, ignoreCase = true) -> Locale.ENGLISH
            languageCode.equals(LANG_ZH_TW, ignoreCase = true) ||
                    languageCode.equals("zh-Hant", ignoreCase = true) ||
                    languageCode.startsWith("zh", ignoreCase = true) -> Locale.TRADITIONAL_CHINESE
            else -> null
        }
    }

    fun createConfiguration(context: Context, languageCode: String): Configuration {
        val base = if (context is LocalizedContextWrapper) context.baseContext else context
        val config = Configuration(base.resources.configuration)
        val locale = getLocale(languageCode)
        if (locale != null) {
            Locale.setDefault(locale)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
        }
        return config
    }

    fun createLocalizedContext(context: Context, languageCode: String): Context {
        val locale = getLocale(languageCode) ?: return context
        val base = if (context is LocalizedContextWrapper) context.baseContext else context
        val config = Configuration(base.resources.configuration)
        Locale.setDefault(locale)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val localizedContext = base.createConfigurationContext(config)
        return LocalizedContextWrapper(base, localizedContext)
    }
}

