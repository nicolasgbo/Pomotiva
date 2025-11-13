package com.ifpr.androidapptemplate.ui.pomodoro

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object GoalsUiHelper {
    data class PeriodViews(
        val summaryCard: View,
        val formContainer: View,
        val titleDate: TextView?,
        val titleCycles: TextView?,
        val tasksListSummary: LinearLayout?
    )

    fun applySummaryState(views: PeriodViews, period: PomodoroRepository.PeriodData) {
        views.summaryCard.visibility = View.VISIBLE
        views.formContainer.visibility = View.GONE
        period.created_at.takeIf { it > 0L }?.let { ts ->
            views.titleDate?.text = formatDateLabel(ts)
        }
        period.cycles_target.takeIf { it > 0L }?.let { c ->
            views.titleCycles?.text = "Ciclos definidos: $c"
        }
        views.tasksListSummary?.let { ll ->
            ll.removeAllViews()
            if (period.tasks.isEmpty()) {
                // Sem tarefas: deixar vazio para o Fragment decidir exibir placeholder
            } else {
                // O Fragment pode preencher linhas se desejar; aqui mantemos simples
            }
        }
    }

    fun applyEmptyFormState(views: PeriodViews) {
        views.summaryCard.visibility = View.GONE
        views.formContainer.visibility = View.VISIBLE
    }

    fun onPrefill(period: PomodoroRepository.PeriodData, callback: (tasks: List<String>) -> Unit) {
        callback(period.tasks.values.toList())
    }

    private fun formatDateLabel(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt","BR"))
        return "Data de criação: ${sdf.format(java.util.Date(ts))}"
    }
}
