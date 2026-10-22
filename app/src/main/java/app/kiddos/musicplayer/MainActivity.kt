package app.kiddos.musicplayer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.kiddos.musicplayer.ui.MusicApp
import app.kiddos.musicplayer.ui.MusicViewModel
import app.kiddos.musicplayer.ui.theme.MusicPlayerTheme
import app.kiddos.musicplayer.util.LocaleHelper

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getSavedLanguage(newBase)
        val localized = LocaleHelper.createLocalizedContext(newBase, lang)
        super.attachBaseContext(localized)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentLanguage by viewModel.currentLanguage.collectAsState()
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, currentLanguage) {
                LocaleHelper.createLocalizedContext(baseContext, currentLanguage)
            }
            val localizedConfiguration = remember(baseContext, currentLanguage) {
                LocaleHelper.createConfiguration(baseContext, currentLanguage)
            }
            val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current ?: this

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalActivityResultRegistryOwner provides activityResultRegistryOwner
            ) {
                MusicPlayerTheme {
                    MusicApp(viewModel = viewModel)
                }
            }
        }
    }
}