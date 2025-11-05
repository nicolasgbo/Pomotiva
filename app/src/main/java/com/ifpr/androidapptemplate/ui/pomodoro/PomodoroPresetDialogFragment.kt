package com.ifpr.androidapptemplate.ui.pomodoro

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.ifpr.androidapptemplate.R

class PomodoroPresetDialogFragment : DialogFragment() {

    private val pomodoroViewModel: PomodoroViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_pomodoro_presets, null)

        // RadioButtons e linhas (para clique em toda a área)
        val rbBeginner = view.findViewById<RadioButton>(R.id.rb_beginner)
        val rbStandard = view.findViewById<RadioButton>(R.id.rb_standard)
        val rbFocus = view.findViewById<RadioButton>(R.id.rb_focus)
        val rbImmersion = view.findViewById<RadioButton>(R.id.rb_immersion)
        val rbCustom = view.findViewById<RadioButton>(R.id.rb_custom)

        val rowBeginner = view.findViewById<View>(R.id.row_beginner)
        val rowStandard = view.findViewById<View>(R.id.row_standard)
        val rowFocus = view.findViewById<View>(R.id.row_focus)
        val rowImmersion = view.findViewById<View>(R.id.row_immersion)
        val rowCustom = view.findViewById<View>(R.id.row_custom)
        val customContainer = view.findViewById<View>(R.id.custom_container)
        val seekFocus = view.findViewById<SeekBar>(R.id.seek_focus)
        val seekBreak = view.findViewById<SeekBar>(R.id.seek_break)
        val labelFocus = view.findViewById<TextView>(R.id.label_custom_focus)
        val labelBreak = view.findViewById<TextView>(R.id.label_custom_break)
        val btnApply = view.findViewById<Button>(R.id.btn_apply)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnClose = view.findViewById<ImageButton>(R.id.btn_close)

        fun updateLabels() {
            labelFocus.text = getString(R.string.pomodoro_focus_label, seekFocus.progress)
            labelBreak.text = getString(R.string.pomodoro_break_label, seekBreak.progress)
        }

        // Helper para selecionar um RadioButton e atualizar UI
        val allRadios = listOf(rbBeginner, rbStandard, rbFocus, rbImmersion, rbCustom)
        fun select(which: RadioButton) {
            allRadios.forEach { it.isChecked = (it == which) }
            customContainer.visibility = if (which == rbCustom) View.VISIBLE else View.GONE
        }

        // Listeners de clique para linhas e próprios radios
        rowBeginner.setOnClickListener { select(rbBeginner) }
        rowStandard.setOnClickListener { select(rbStandard) }
        rowFocus.setOnClickListener { select(rbFocus) }
        rowImmersion.setOnClickListener { select(rbImmersion) }
        rowCustom.setOnClickListener { select(rbCustom) }

        rbBeginner.setOnClickListener { select(rbBeginner) }
        rbStandard.setOnClickListener { select(rbStandard) }
        rbFocus.setOnClickListener { select(rbFocus) }
        rbImmersion.setOnClickListener { select(rbImmersion) }
        rbCustom.setOnClickListener { select(rbCustom) }

        seekFocus.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateLabels() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        seekBreak.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateLabels() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        updateLabels()

        btnCancel.setOnClickListener { dismiss() }
        btnClose.setOnClickListener { dismiss() }

        // Estado inicial: manter seleção padrão do layout (standard) e garantir UI correta
        select(if (rbStandard.isChecked) rbStandard else rbBeginner)

        btnApply.setOnClickListener {
            when {
                rbBeginner.isChecked -> pomodoroViewModel.setDurations(workMin = 15, shortBreakMin = 5, longBreakMin = 15)
                rbStandard.isChecked -> pomodoroViewModel.setDurations(workMin = 25, shortBreakMin = 5, longBreakMin = 15)
                rbFocus.isChecked -> pomodoroViewModel.setDurations(workMin = 40, shortBreakMin = 15, longBreakMin = 15)
                rbImmersion.isChecked -> pomodoroViewModel.setDurations(workMin = 60, shortBreakMin = 15, longBreakMin = 15)
                rbCustom.isChecked -> {
                    val w = seekFocus.progress.coerceAtLeast(1)
                    val b = seekBreak.progress.coerceAtLeast(1)
                    pomodoroViewModel.setDurations(workMin = w, shortBreakMin = b, longBreakMin = 15)
                }
                else -> pomodoroViewModel.setDurations(workMin = 25, shortBreakMin = 5, longBreakMin = 15)
            }
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
}

