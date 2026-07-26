package com.dark.badlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.dark.badlock.data.CacheManager
import com.dark.badlock.data.ModuleRepository
import com.dark.badlock.ui.BadlockViewModel
import com.dark.badlock.ui.screens.MainScreen
import com.dark.badlock.ui.theme.BadlockTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BadlockViewModel by viewModels {
        val cacheManager = CacheManager(applicationContext)
        val repository = ModuleRepository(applicationContext, cacheManager)
        BadlockViewModel.Factory(repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()
        setContent {
            BadlockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}
