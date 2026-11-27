package com.heaventreat.obhod.preferences

import android.content.Context
import android.content.SharedPreferences

internal fun Context.privatePreferences(name: String): SharedPreferences =
    getSharedPreferences(name, Context.MODE_PRIVATE)
