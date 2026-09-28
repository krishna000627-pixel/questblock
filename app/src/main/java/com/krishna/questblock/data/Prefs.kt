package com.krishna.questblock.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Prefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    var strictMode: Boolean
        get() = sp.getBoolean("strict", false)
        set(value) = sp.edit().putBoolean("strict", value).apply()
}
