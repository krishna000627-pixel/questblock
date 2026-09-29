package com.krishna.questblock.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.krishna.questblock.data.Prefs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: Prefs

    // Reactive state — changing this triggers recompose without restart
    private var onboardingDone by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onboardingDone = prefs.onboardingDone
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            QuestTheme {
                if (onboardingDone) {
                    MainScreen()
                } else {
                    OnboardingScreen(onDone = {
                        onboardingDone = true   // triggers recompose → MainScreen instantly
                    })
                }
            }
        }
    }
}
