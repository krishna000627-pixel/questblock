package com.krishna.questblock.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.krishna.questblock.service.BlockerAccessibilityService
import com.krishna.questblock.service.QuestAdminReceiver

object Perms {
    fun accessibilityEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, BlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun overlayGranted(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun batteryExempt(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun adminComponent(ctx: Context) = ComponentName(ctx, QuestAdminReceiver::class.java)

    fun adminActive(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(ctx))
}
