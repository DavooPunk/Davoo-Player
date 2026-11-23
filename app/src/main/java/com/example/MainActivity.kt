package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.DeviceMusicScanner
import com.example.data.repository.LastFmRepository
import com.example.ui.SonoraApp
import com.example.ui.components.AudioArtImageLoaderProvider
import com.example.ui.components.RemoteArtImageLoaderProvider
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.SonoraTheme
import com.example.ui.viewmodel.SonoraViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            val sonoraViewModel: SonoraViewModel = viewModel()
            val appearancePreferences by sonoraViewModel.appearancePreferences.collectAsStateWithLifecycle()
            SonoraTheme(appearance = appearancePreferences) {
                SonoraApp(viewModel = sonoraViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AudioArtImageLoaderProvider.trimMemory(level)
        RemoteArtImageLoaderProvider.trimMemory(level)
        ArtworkColorExtractor.trimMemory(level)
        LastFmRepository.trimActiveCaches(level)
        DeviceMusicScanner.trimMemory(level)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        val level = android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        AudioArtImageLoaderProvider.trimMemory(level)
        RemoteArtImageLoaderProvider.trimMemory(level)
        ArtworkColorExtractor.trimMemory(level)
        LastFmRepository.trimActiveCaches(level)
        DeviceMusicScanner.trimMemory(level)
    }
}
