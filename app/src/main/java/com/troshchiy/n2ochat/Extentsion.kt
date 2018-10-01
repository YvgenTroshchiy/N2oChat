package com.troshchiy.n2ochat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup


fun Context.string(res: Int, vararg args: Any): String = resources.getString(res, *args)

fun ViewGroup.inflate(layoutId: Int, attachToRoot: Boolean = false): View =
    context.inflater().inflate(layoutId, this, attachToRoot)

fun Context.inflater(): LayoutInflater = LayoutInflater.from(this)