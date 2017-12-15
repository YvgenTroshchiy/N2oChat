package com.troshchiy.n2ochat

import android.content.Context

fun Context.string(res: Int, vararg args: Any): String = resources.getString(res, *args)