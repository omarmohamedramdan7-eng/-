package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.resolveStrings
import com.example.ui.navigation.OmarNavRoot
import com.example.ui.theme.OmarVpnTheme
import com.example.ui.viewmodel.OmarVpnViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val viewModel: OmarVpnViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val strings = resolveStrings(settings.language)

            CompositionLocalProvider(LocalStrings provides strings) {
                OmarVpnTheme(isArabic = strings.isArabic) {
                    OmarNavRoot(viewModel = viewModel)
                }
            }
        }
    }
}
