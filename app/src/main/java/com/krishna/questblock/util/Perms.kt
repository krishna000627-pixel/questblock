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

    /** True once `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` has been run (survives reboots and updates). */
    fun secureSettingsGranted(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * v301: if the system or the user switched our accessibility service off and the secure-settings grant exists,
     * put it back in ENABLED_ACCESSIBILITY_SERVICES. Does nothing without the grant. Returns true when enabled afterwards.
     */
    fun ensureAccessibilityOn(ctx: Context): Boolean {
        if (accessibilityEnabled(ctx)) return true
        if (!secureSettingsGranted(ctx)) return false
        return runCatching {
            val me = ComponentName(ctx, BlockerAccessibilityService::class.java).flattenToString()
            val cr = ctx.contentResolver
            val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val list = cur.split(':').filter { it.isNotBlank() }
            if (list.none { ComponentName.unflattenFromString(it) == ComponentName.unflattenFromString(me) }) {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (list + me).joinToString(":"))
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            com.krishna.questblock.util.DebugLog.d("Accessibility re-enabled via WRITE_SECURE_SETTINGS")
            true
        }.getOrDefault(false)
    }

    /** Music-detection access is only restored if it was seen ON before (never forced on for someone who never wanted it). */
    fun ensureNotificationAccessOn(ctx: Context): Boolean {
        val mem = ctx.getSharedPreferences("qb_perm_memory", Context.MODE_PRIVATE)
        if (notificationAccess(ctx)) { mem.edit().putBoolean("notif_was_on", true).apply(); return true }
        if (!secureSettingsGranted(ctx) || !mem.getBoolean("notif_was_on", false)) return false
        return runCatching {
            val me = ComponentName(ctx, com.krishna.questblock.service.MediaListenerService::class.java)
            val cr = ctx.contentResolver
            val cur = Settings.Secure.getString(cr, "enabled_notification_listeners").orEmpty()
            val list = cur.split(':').filter { it.isNotBlank() }
            if (list.none { ComponentName.unflattenFromString(it) == me }) {
                Settings.Secure.putString(cr, "enabled_notification_listeners", (list + me.flattenToString()).joinToString(":"))
            }
            com.krishna.questblock.util.DebugLog.d("Notification access re-enabled via WRITE_SECURE_SETTINGS")
            true
        }.getOrDefault(false)
    }

    /** v302: everything the secure-settings grant can put back (accessibility, music access). */
    fun keepPermsOn(ctx: Context) {
        ensureAccessibilityOn(ctx)
        ensureNotificationAccessOn(ctx)
    }

    fun overlayGranted(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun batteryExempt(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun notificationsOk(ctx: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun notificationAccess(ctx: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun adminComponent(ctx: Context) = ComponentName(ctx, QuestAdminReceiver::class.java)

    fun adminActive(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(ctx))
}
