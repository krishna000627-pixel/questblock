package com.krishna.questblock.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Per-brand shortcuts to the "autostart / background launch" screens.
 * These are internal screens of each vendor's system app, so names differ between OS versions;
 * each candidate is tried in turn and the first that opens wins. If none do, we open App info.
 */
object Autostart {

    private fun comp(pkg: String, cls: String) =
        Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private val oppoFamily = listOf(
        comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        comp("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        comp("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
        comp("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
    )

    private fun candidates(): List<Intent> {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> listOf(
                comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            "oppo" in m || "realme" in m -> oppoFamily
            "oneplus" in m -> listOf(
                comp("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")) + oppoFamily
            "vivo" in m || "iqoo" in m -> listOf(
                comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
            "huawei" in m || "honor" in m -> listOf(
                comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                comp("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            "samsung" in m -> listOf(
                comp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                comp("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                comp("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            else -> emptyList()
        }
    }

    /** Opens the brand's autostart screen. Returns true if a brand-specific screen opened, false if we fell back to App info. */
    fun open(ctx: Context): Boolean {
        for (i in candidates()) {
            if (runCatching { ctx.startActivity(i) }.isSuccess) return true
        }
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return false
    }

    fun brandName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /** Where "lock in recents" lives, in plain words, for the guide. */
    fun recentsHint(): String {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> "Open Recents, long-press the QuestBlock card, tap the lock icon."
            "oppo" in m || "realme" in m || "oneplus" in m -> "Open Recents, tap the ⋮ (or long-press the card) on QuestBlock, choose Lock."
            "vivo" in m || "iqoo" in m -> "Open Recents, long-press the QuestBlock card, tap Lock."
            "huawei" in m || "honor" in m -> "Open Recents, pull the QuestBlock card down until a padlock appears."
            "samsung" in m -> "Open Recents, tap the QuestBlock icon at the top of its card, choose Keep open."
            else -> "Open Recents, then long-press or tap the menu on the QuestBlock card and choose Lock / Keep open."
        }
    }
}
