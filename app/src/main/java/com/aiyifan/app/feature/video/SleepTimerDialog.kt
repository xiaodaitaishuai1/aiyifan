package com.aiyifan.app.feature.video

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import com.aiyifan.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object SleepTimerDialog {
    fun label(context: Context, state: SleepTimerState): String = when (state.option) {
        SleepTimerOption.OFF -> context.getString(R.string.sleep_timer_title)
        SleepTimerOption.END_OF_EPISODE -> context.getString(R.string.sleep_timer_end_label)
        else -> {
            val seconds = ((state.remainingMs ?: 0L) + 999L) / 1_000L
            context.getString(R.string.sleep_timer_countdown, seconds / 60L, seconds % 60L)
        }
    }

    fun show(context: Context, controller: VideoPlaybackController, windowType: Int? = null): AlertDialog {
        val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
        val labels = intArrayOf(R.string.sleep_timer_off, R.string.sleep_timer_15, R.string.sleep_timer_30,
            R.string.sleep_timer_60, R.string.sleep_timer_end)
        val options = SleepTimerOption.entries
        val textColor = ContextCompat.getColor(themedContext, R.color.text_primary)
        val accentColor = ContextCompat.getColor(themedContext, R.color.accent)
        val checkColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accentColor, ContextCompat.getColor(themedContext, R.color.text_secondary)),
        )
        val adapter = object : ArrayAdapter<String>(themedContext, android.R.layout.simple_list_item_single_choice,
            labels.map(context::getString)) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as CheckedTextView).apply {
                    setTextColor(textColor)
                    checkMarkTintList = checkColors
                }
        }
        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setBackground(GradientDrawable().apply {
                setColor(ContextCompat.getColor(themedContext, R.color.surface))
                cornerRadius = 16f * themedContext.resources.displayMetrics.density
            })
            .setTitle(label(context, controller.sleepTimerState))
            .setSingleChoiceItems(adapter, controller.sleepTimerState.option.ordinal) { selected, index ->
                controller.setSleepTimer(options[index])
                selected.dismiss()
            }
            .setNegativeButton(R.string.sleep_timer_close, null)
            .create()
        windowType?.let { dialog.window?.setType(it) }
        val removeListener = controller.addStateListener { dialog.setTitle(label(context, controller.sleepTimerState)) }
        dialog.setOnDismissListener { removeListener() }
        try {
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accentColor)
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(textColor)
        } catch (error: RuntimeException) {
            removeListener()
            throw error
        }
        return dialog
    }
}
