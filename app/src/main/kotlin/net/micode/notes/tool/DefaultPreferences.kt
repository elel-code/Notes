package net.micode.notes.tool

import android.content.Context
import android.content.SharedPreferences

private const val DEFAULT_PREFERENCES_SUFFIX = "_preferences"

fun Context.defaultPreferences(): SharedPreferences =
    getSharedPreferences("${packageName}$DEFAULT_PREFERENCES_SUFFIX", Context.MODE_PRIVATE)
