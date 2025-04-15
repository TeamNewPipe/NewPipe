package org.schabi.newpipe.ktx

import android.content.Intent

// isEmpty unparcels the extras
fun Intent.extrasString(): String = extras?.takeIf { !it.isEmpty }?.toString() ?: ""
