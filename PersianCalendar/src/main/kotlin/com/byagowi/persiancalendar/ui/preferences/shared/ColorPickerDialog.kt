package com.byagowi.persiancalendar.ui.preferences.shared

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.view.setPadding
import com.byagowi.persiancalendar.DEFAULT_SELECTED_WIDGET_BACKGROUND_COLOR
import com.byagowi.persiancalendar.DEFAULT_SELECTED_WIDGET_TEXT_COLOR
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.ui.utils.dp
import com.byagowi.persiancalendar.utils.appPrefs
import java.util.*

fun showColorPickerDialog(context: Context, isBackgroundPick: Boolean, key: String) {
    val initialColor = context.appPrefs.getString(key, null)
        ?: if (isBackgroundPick) DEFAULT_SELECTED_WIDGET_BACKGROUND_COLOR else DEFAULT_SELECTED_WIDGET_TEXT_COLOR
    showColorPickerDialog(context, isBackgroundPick, initialColor) { colorResult ->
        context.appPrefs.edit { this.putString(key, colorResult) }
    }
}

private fun showColorPickerDialog(
    context: Context, isBackgroundPick: Boolean, initialColor: String, onResult: (String) -> Unit
) {
    val colorPickerView = ColorPickerView(context).also {
        it.setColorsToPick(
            if (isBackgroundPick) listOf(0x00000000L, 0x20000000L, 0x50000000L, 0xFF000000L)
            else listOf(0xFFFFFFFFL, 0xFFE65100L, 0xFF00796bL, 0xFFFEF200L, 0xFF202020L)
        )
        if (!isBackgroundPick) it.hideAlphaSeekBar()
        it.setPickedColor(Color.parseColor(initialColor))
        it.setPadding(10.dp.toInt())
    }
    AlertDialog.Builder(context)
        .setTitle(if (isBackgroundPick) R.string.widget_background_color else R.string.widget_text_color)
        .setView(colorPickerView)
        .setPositiveButton(R.string.accept) { _, _ ->
            onResult(
                if (isBackgroundPick) "#%08X".format(
                    Locale.ENGLISH, 0xFFFFFFFF and colorPickerView.pickerColor.toLong()
                ) else "#%06X".format(
                    Locale.ENGLISH, 0xFFFFFF and colorPickerView.pickerColor
                )
            )
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}