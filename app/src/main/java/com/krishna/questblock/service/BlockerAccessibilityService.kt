package com.krishna.questblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.ui.BlockActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlockerAccessibilityService : AccessibilityService() {

    @Inject lateinit var repo: GameRepository
    @Inject lateinit var prefs: Prefs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentPkg: String? = null
    private var studyJob: Job? = null
    private var studyPkg: String? = null
    private var studyMinutes: Int = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pkg in IGNORED_PKGS || pkg == imePackage()) return

        if (pkg != currentPkg) {
            // App switched
            if (studyPkg != null && pkg != studyPkg) endStudyTimer()
            currentPkg = pkg
            scope.launch { handleApp(pkg) }
        }
    }

    private suspend fun handleApp(pkg: String) {
        val app = repo.getBlockedApp(pkg) ?: return
        val now = System.currentTimeMillis()

        when {
            app.isStudyApp -> startStudyTimer(pkg, app.label)
            app.unlockedUntil > now -> {
                // Unlocked — schedule re-check when it expires
                val delay = app.unlockedUntil - now + 500
                scope.launch { delay(delay); if (currentPkg == pkg) handleApp(pkg) }
            }
            else -> showBlock(pkg)
        }
    }

    private fun startStudyTimer(pkg: String, label: String) {
        if (studyPkg == pkg) return
        endStudyTimer()
        studyPkg = pkg
        studyMinutes = 0
        studyJob = scope.launch {
            while (true) {
                delay(60_000L)
                if (currentPkg != pkg) break
                studyMinutes++
                if (studyMinutes % 5 == 0) repo.creditStudyCoins(pkg, label, 5)
            }
        }
    }

    private fun endStudyTimer() {
        studyJob?.cancel(); studyJob = null; studyPkg = null; studyMinutes = 0
    }

    private fun showBlock(pkg: String) {
        startActivity(Intent(this, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_PKG, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    private fun imePackage() = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.substringBefore('/')

    override fun onInterrupt() = Unit
    override fun onDestroy() { endStudyTimer(); scope.cancel(); super.onDestroy() }

    companion object {
        val IGNORED_PKGS = setOf("com.android.systemui", "android")
    }
}
