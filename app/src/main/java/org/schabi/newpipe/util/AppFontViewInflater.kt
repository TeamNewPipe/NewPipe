/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.AppCompatToggleButton
import com.google.android.material.theme.MaterialComponentsViewInflater

/** Retains Material widget classes, attributes and themed contexts. */
@Keep
class AppFontViewInflater : MaterialComponentsViewInflater() {
    override fun createTextView(context: Context, attrs: AttributeSet): AppCompatTextView = AppFonts.inflated(super.createTextView(context, attrs))

    override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton = AppFonts.inflated(super.createButton(context, attrs))

    override fun createCheckBox(context: Context, attrs: AttributeSet): AppCompatCheckBox = AppFonts.inflated(super.createCheckBox(context, attrs))

    override fun createRadioButton(context: Context, attrs: AttributeSet): AppCompatRadioButton = AppFonts.inflated(super.createRadioButton(context, attrs))

    override fun createAutoCompleteTextView(context: Context, attrs: AttributeSet?): AppCompatAutoCompleteTextView = AppFonts.inflated(super.createAutoCompleteTextView(context, attrs))

    override fun createEditText(context: Context, attrs: AttributeSet): AppCompatEditText = AppFonts.inflated(super.createEditText(context, attrs))

    override fun createCheckedTextView(context: Context, attrs: AttributeSet): AppCompatCheckedTextView = AppFonts.inflated(super.createCheckedTextView(context, attrs))

    override fun createMultiAutoCompleteTextView(context: Context, attrs: AttributeSet): AppCompatMultiAutoCompleteTextView = AppFonts.inflated(super.createMultiAutoCompleteTextView(context, attrs))

    override fun createToggleButton(context: Context, attrs: AttributeSet): AppCompatToggleButton = AppFonts.inflated(super.createToggleButton(context, attrs))
}
