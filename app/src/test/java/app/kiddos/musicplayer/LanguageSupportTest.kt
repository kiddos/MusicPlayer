package app.kiddos.musicplayer

import app.kiddos.musicplayer.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class LanguageSupportTest {

    @Test
    fun localeHelper_supportedLanguagesContainsExpectedCodes() {
        assertEquals(3, LocaleHelper.supportedLanguages.size)
        assertEquals(LocaleHelper.LANG_SYSTEM, LocaleHelper.supportedLanguages[0])
        assertEquals(LocaleHelper.LANG_EN, LocaleHelper.supportedLanguages[1])
        assertEquals(LocaleHelper.LANG_ZH_TW, LocaleHelper.supportedLanguages[2])
    }

    @Test
    fun localeHelper_getLocale_mapsCorrectly() {
        // System default returns null so system configuration is respected
        assertNull(LocaleHelper.getLocale(LocaleHelper.LANG_SYSTEM))

        // English
        val enLocale = LocaleHelper.getLocale(LocaleHelper.LANG_EN)
        assertNotNull(enLocale)
        assertEquals(Locale.ENGLISH.language, enLocale?.language)

        // Traditional Chinese
        val zhLocale = LocaleHelper.getLocale(LocaleHelper.LANG_ZH_TW)
        assertNotNull(zhLocale)
        assertEquals("zh", zhLocale?.language)
        assertEquals("TW", zhLocale?.country)
    }

    @Test
    fun localeHelper_unknownLanguage_returnsNull() {
        assertNull(LocaleHelper.getLocale("unknown"))
        assertNull(LocaleHelper.getLocale("fr"))
    }
}
