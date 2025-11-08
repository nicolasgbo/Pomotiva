package com.ifpr.androidapptemplate.ui.metas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ifpr.androidapptemplate.databinding.FragmentMetasBinding
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import android.content.res.ColorStateList
import android.view.Gravity
import android.util.TypedValue
import com.google.android.material.textfield.TextInputEditText
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.Typeface
import android.text.style.StyleSpan
import java.text.SimpleDateFormat
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Calendar
import java.util.Locale
import com.ifpr.androidapptemplate.ui.metas.MetasViewModel

class MetasFragment : Fragment() {

    private var _binding: FragmentMetasBinding? = null
    private val binding get() = _binding!!
    // Lista temporária das tarefas diárias (antes de persistir)
    private val tempDailyTasks = mutableListOf<String>()
    // Listas temporárias para semanal, mensal e anual
    private val tempWeeklyTasks = mutableListOf<String>()
    private val tempMonthlyTasks = mutableListOf<String>()
    private val tempYearlyTasks = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val metasViewModel =
            ViewModelProvider(this).get(MetasViewModel::class.java)

        _binding = FragmentMetasBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Ajuste dinâmico de padding inferior para evitar sobreposição pela BottomNavigation
        root.post {
            val nav = activity?.findViewById<BottomNavigationView>(com.ifpr.androidapptemplate.R.id.nav_view)
            val shadow = activity?.findViewById<View>(com.ifpr.androidapptemplate.R.id.bottom_nav_top_shadow)
            val extra = dp(8)
            val shadowHeight = shadow?.height ?: (shadow?.layoutParams?.height ?: 0)
            val bottomPadding = (nav?.height ?: 0) + shadowHeight + extra
            val sv = binding.scroll
            if (bottomPadding > 0) {
                sv.setPadding(sv.paddingLeft, sv.paddingTop, sv.paddingRight, bottomPadding)
            }
        }

        // Preenche os subtítulos com data/semana/mês/ano atuais (pt-BR)
        val cal = Calendar.getInstance()
        val locale = Locale("pt", "BR")

        val dailyFmt = SimpleDateFormat("EEEE, dd", locale)
        val monthlyFmt = SimpleDateFormat("MMMM", locale)

        val dailyText = dailyFmt.format(cal.time).replaceFirstChar { it.uppercase(locale) }
        val weekNumber = cal.get(Calendar.WEEK_OF_YEAR)
        val monthText = monthlyFmt.format(cal.time).replaceFirstChar { it.lowercase(locale) }
        val yearText = cal.get(Calendar.YEAR).toString()

        binding.subtitleDaily.text = dailyText
        binding.subtitleWeekly.text = "semana $weekNumber"
        binding.subtitleMonthly.text = monthText
        binding.subtitleYearly.text = yearText

        // Estado em memória para tarefas antes de salvar (apenas diária)

        // Toggle do formulário de meta diária
        binding.btnAddDaily.setOnClickListener {
            val form = binding.formDailyContainer
            // Reset do resumo ao reabrir o form
            if (binding.summaryDailyCard.isVisible) {
                binding.summaryDailyCard.isGone = true
            }
            form.isVisible = !form.isVisible
        }

        // Adicionar tarefa diária à lista temporária e à UI
        binding.btnAddDailyTask.setOnClickListener {
            val text = binding.inputDailyTask.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Digite uma tarefa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addDailyTask(text)
            binding.inputDailyTask.setText("")
        }

        // Salvar meta diária e mostrar resumo
        binding.btnSaveDaily.setOnClickListener {
            val cyclesStr = binding.inputDailyCycles.text?.toString()?.trim().orEmpty()
            val cycles = cyclesStr.toIntOrNull()
            if (cycles == null) {
                Toast.makeText(requireContext(), "Informe um número válido de ciclos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Preenche o resumo com a data e o número de ciclos (aplicando cor somente no número)
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            val dateStr = fmt.format(Calendar.getInstance().time)
            binding.summaryDailyDate.text = buildBoldLabel("Data de criação: ", dateStr)
            binding.summaryDailyCycles.text = buildCyclesSpannable(cycles)
            // Limpa e popula a lista de resumo
            binding.summaryDailyTasksList.removeAllViews()
            if (tempDailyTasks.isEmpty()) {
                addSummaryRow(binding.summaryDailyTasksList, "(Sem tarefas)")
            } else {
                tempDailyTasks.forEach { addSummaryRow(binding.summaryDailyTasksList, it) }
            }

            // Alterna visibilidades
            binding.formDailyContainer.isGone = true
            binding.summaryDailyCard.isVisible = true

            // Atualiza botão principal para "Editar meta"
            binding.btnAddDaily.apply {
                text = "Editar meta"
                setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24)
            }

            // Opcional: feedback
            Toast.makeText(requireContext(), "Meta diária salva", Toast.LENGTH_SHORT).show()
        }

        // Cancelar edição da meta diária (fecha o formulário sem salvar)
        binding.btnCancelDaily.setOnClickListener {
            // Limpa estado da meta diária
            tempDailyTasks.clear()
            binding.dailyTasksList.removeAllViews()
            binding.dailyTasksHeader.isVisible = false

            // Limpa campos
            binding.inputDailyCycles.setText("")
            binding.inputDailyTask.setText("")

            // Esconde resumo (card) e formulário
            binding.summaryDailyCard.isGone = true
            binding.formDailyContainer.isGone = true

            // Restaura botão principal para "Adicionar meta"
            binding.btnAddDaily.apply {
                text = "Adicionar meta"
                setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24)
            }
        }
        // --------- Semanal ---------
        binding.btnAddWeekly.setOnClickListener {
            val form = binding.formWeeklyContainer
            if (binding.summaryWeeklyCard.isVisible) binding.summaryWeeklyCard.isGone = true
            form.isVisible = !form.isVisible
        }
        binding.btnAddWeeklyTask.setOnClickListener {
            val text = binding.inputWeeklyTask.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Digite uma tarefa semanal", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            addWeeklyTask(text)
            binding.inputWeeklyTask.setText("")
        }
        binding.btnSaveWeekly.setOnClickListener {
            val cycles = binding.inputWeeklyCycles.text?.toString()?.trim().orEmpty().toIntOrNull()
            if (cycles == null) { Toast.makeText(requireContext(), "Informe ciclos válidos", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            binding.summaryWeeklyDate.text = buildBoldLabel("Data de criação: ", fmt.format(Calendar.getInstance().time))
            binding.summaryWeeklyCycles.text = buildCyclesSpannable(cycles)
            binding.summaryWeeklyTasksList.removeAllViews()
            if (tempWeeklyTasks.isEmpty()) addSummaryRow(binding.summaryWeeklyTasksList, "(Sem tarefas)") else tempWeeklyTasks.forEach { addSummaryRow(binding.summaryWeeklyTasksList, it) }
            binding.formWeeklyContainer.isGone = true
            binding.summaryWeeklyCard.isVisible = true
            binding.btnAddWeekly.apply { text = "Editar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24) }
            Toast.makeText(requireContext(), "Meta semanal salva", Toast.LENGTH_SHORT).show()
        }
        binding.btnCancelWeekly.setOnClickListener {
            tempWeeklyTasks.clear(); binding.weeklyTasksList.removeAllViews(); binding.weeklyTasksHeader.isVisible = false
            binding.inputWeeklyCycles.setText(""); binding.inputWeeklyTask.setText("")
            binding.summaryWeeklyCard.isGone = true; binding.formWeeklyContainer.isGone = true
            binding.btnAddWeekly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }
        }

        // --------- Mensal ---------
        binding.btnAddMonthly.setOnClickListener {
            val form = binding.formMonthlyContainer
            if (binding.summaryMonthlyCard.isVisible) binding.summaryMonthlyCard.isGone = true
            form.isVisible = !form.isVisible
        }
        binding.btnAddMonthlyTask.setOnClickListener {
            val text = binding.inputMonthlyTask.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) { Toast.makeText(requireContext(), "Digite uma tarefa mensal", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            addMonthlyTask(text); binding.inputMonthlyTask.setText("")
        }
        binding.btnSaveMonthly.setOnClickListener {
            val cycles = binding.inputMonthlyCycles.text?.toString()?.trim().orEmpty().toIntOrNull()
            if (cycles == null) { Toast.makeText(requireContext(), "Informe ciclos válidos", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            binding.summaryMonthlyDate.text = buildBoldLabel("Data de criação: ", fmt.format(Calendar.getInstance().time))
            binding.summaryMonthlyCycles.text = buildCyclesSpannable(cycles)
            binding.summaryMonthlyTasksList.removeAllViews()
            if (tempMonthlyTasks.isEmpty()) addSummaryRow(binding.summaryMonthlyTasksList, "(Sem tarefas)") else tempMonthlyTasks.forEach { addSummaryRow(binding.summaryMonthlyTasksList, it) }
            binding.formMonthlyContainer.isGone = true; binding.summaryMonthlyCard.isVisible = true
            binding.btnAddMonthly.apply { text = "Editar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24) }
            Toast.makeText(requireContext(), "Meta mensal salva", Toast.LENGTH_SHORT).show()
        }
        binding.btnCancelMonthly.setOnClickListener {
            tempMonthlyTasks.clear(); binding.monthlyTasksList.removeAllViews(); binding.monthlyTasksHeader.isVisible = false
            binding.inputMonthlyCycles.setText(""); binding.inputMonthlyTask.setText("")
            binding.summaryMonthlyCard.isGone = true; binding.formMonthlyContainer.isGone = true
            binding.btnAddMonthly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }
        }

        // --------- Anual ---------
        binding.btnAddYearly.setOnClickListener {
            val form = binding.formYearlyContainer
            if (binding.summaryYearlyCard.isVisible) binding.summaryYearlyCard.isGone = true
            form.isVisible = !form.isVisible
        }
        binding.btnAddYearlyTask.setOnClickListener {
            val text = binding.inputYearlyTask.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) { Toast.makeText(requireContext(), "Digite uma tarefa anual", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            addYearlyTask(text); binding.inputYearlyTask.setText("")
        }
        binding.btnSaveYearly.setOnClickListener {
            val cycles = binding.inputYearlyCycles.text?.toString()?.trim().orEmpty().toIntOrNull()
            if (cycles == null) { Toast.makeText(requireContext(), "Informe ciclos válidos", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            binding.summaryYearlyDate.text = buildBoldLabel("Data de criação: ", fmt.format(Calendar.getInstance().time))
            binding.summaryYearlyCycles.text = buildCyclesSpannable(cycles)
            binding.summaryYearlyTasksList.removeAllViews()
            if (tempYearlyTasks.isEmpty()) addSummaryRow(binding.summaryYearlyTasksList, "(Sem tarefas)") else tempYearlyTasks.forEach { addSummaryRow(binding.summaryYearlyTasksList, it) }
            binding.formYearlyContainer.isGone = true; binding.summaryYearlyCard.isVisible = true
            binding.btnAddYearly.apply { text = "Editar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24) }
            Toast.makeText(requireContext(), "Meta anual salva", Toast.LENGTH_SHORT).show()
        }
        binding.btnCancelYearly.setOnClickListener {
            tempYearlyTasks.clear(); binding.yearlyTasksList.removeAllViews(); binding.yearlyTasksHeader.isVisible = false
            binding.inputYearlyCycles.setText(""); binding.inputYearlyTask.setText("")
            binding.summaryYearlyCard.isGone = true; binding.formYearlyContainer.isGone = true
            binding.btnAddYearly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }
        }

        return root
    }

    private fun addTaskRow(container: LinearLayout, text: String) {
        // Mantido para compatibilidade (usado em resumos), cria apenas TextView simples
        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(com.ifpr.androidapptemplate.R.color.black, null))
            textSize = 14f
        }
        container.addView(tv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun addDailyTask(text: String) {
        tempDailyTasks.add(text)
        rebuildDailyTaskList()
    }

    private fun rebuildDailyTaskList() {
        val list = binding.dailyTasksList
        list.removeAllViews()
        tempDailyTasks.forEachIndexed { index, t ->
            list.addView(createDailyTaskRow(index, t))
        }
        // Controla visibilidade do cabeçalho "Tarefas atuais"
        binding.dailyTasksHeader.isVisible = tempDailyTasks.isNotEmpty()
    }

    private fun createDailyTaskRow(index: Int, text: String): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        // Bolinha roxa antes da tarefa
        val dotSize = dp(8)
        val dot = View(requireContext()).apply {
            background = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.circle_dot)
            backgroundTintList = ColorStateList.valueOf(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
        }
        val dotParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
            rightMargin = dp(12)
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(dot, dotParams)

        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(com.ifpr.androidapptemplate.R.color.black, null))
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv, tvParams)

        val editBtn = TextView(requireContext()).apply {
            contentDescription = "Editar tarefa"
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val drawable = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.ic_edit_24)
            drawable?.let {
                val size = dp(20)
                it.setBounds(0, 0, size, size)
                it.setTint(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
                setCompoundDrawables(it, null, null, null)
            }
            compoundDrawablePadding = 0
            setOnClickListener { showEditDailyTaskDialog(index) }
        }
        row.addView(editBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val delBtn = TextView(requireContext()).apply {
            contentDescription = "Excluir tarefa"
            setPadding(dp(12), dp(8), 0, dp(8))
            val drawable = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.ic_close_24)
            drawable?.let {
                val size = dp(20)
                it.setBounds(0, 0, size, size)
                it.setTint(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
                setCompoundDrawables(it, null, null, null)
            }
            compoundDrawablePadding = 0
            setOnClickListener {
                if (index in tempDailyTasks.indices) {
                    tempDailyTasks.removeAt(index)
                    rebuildDailyTaskList()
                }
            }
        }
        row.addView(delBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return row
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    private fun showEditDailyTaskDialog(index: Int) {
        if (index !in tempDailyTasks.indices) return

        // Infla layout customizado do diálogo
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(com.ifpr.androidapptemplate.R.layout.dialog_edit_task, null)

        val input = dialogView.findViewById<TextInputEditText>(com.ifpr.androidapptemplate.R.id.edit_task_input)
        val btnSave = dialogView.findViewById<View>(com.ifpr.androidapptemplate.R.id.btn_save)
        val btnCancel = dialogView.findViewById<View>(com.ifpr.androidapptemplate.R.id.btn_cancel)
        val btnClose = dialogView.findViewById<ImageView>(com.ifpr.androidapptemplate.R.id.img_close)

        input?.apply {
            setText(tempDailyTasks[index])
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val newText = input?.text?.toString()?.trim().orEmpty()
            if (newText.isNotEmpty()) {
                tempDailyTasks[index] = newText
                rebuildDailyTaskList()
            }
            dialog.dismiss()
        }

        val dismissAction: (View) -> Unit = {
            dialog.dismiss()
        }
        btnCancel.setOnClickListener(dismissAction)
        btnClose.setOnClickListener(dismissAction)

        dialog.show()
    }

    // --------- Semanal: adicionar, listar, editar e remover ---------
    private fun addWeeklyTask(text: String) {
        tempWeeklyTasks.add(text)
        rebuildWeeklyTaskList()
    }

    private fun rebuildWeeklyTaskList() {
        val list = binding.weeklyTasksList
        list.removeAllViews()
        tempWeeklyTasks.forEachIndexed { index, t ->
            list.addView(createWeeklyTaskRow(index, t))
        }
        binding.weeklyTasksHeader.isVisible = tempWeeklyTasks.isNotEmpty()
    }

    private fun createWeeklyTaskRow(index: Int, text: String): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        val dotSize = dp(8)
        val dot = View(requireContext()).apply {
            background = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.circle_dot)
            backgroundTintList = ColorStateList.valueOf(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
        }
        val dotParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
            rightMargin = dp(12)
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(dot, dotParams)

        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(com.ifpr.androidapptemplate.R.color.black, null))
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv, tvParams)

        val editBtn = TextView(requireContext()).apply {
            contentDescription = "Editar tarefa"
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val drawable = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.ic_edit_24)
            drawable?.let {
                val size = dp(20)
                it.setBounds(0, 0, size, size)
                it.setTint(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
                setCompoundDrawables(it, null, null, null)
            }
            compoundDrawablePadding = 0
            setOnClickListener { showEditWeeklyTaskDialog(index) }
        }
        row.addView(editBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val delBtn = TextView(requireContext()).apply {
            contentDescription = "Excluir tarefa"
            setPadding(dp(12), dp(8), 0, dp(8))
            val drawable = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.ic_close_24)
            drawable?.let {
                val size = dp(20)
                it.setBounds(0, 0, size, size)
                it.setTint(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
                setCompoundDrawables(it, null, null, null)
            }
            compoundDrawablePadding = 0
            setOnClickListener {
                if (index in tempWeeklyTasks.indices) {
                    tempWeeklyTasks.removeAt(index)
                    rebuildWeeklyTaskList()
                }
            }
        }
        row.addView(delBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return row
    }

    private fun showEditWeeklyTaskDialog(index: Int) {
        if (index !in tempWeeklyTasks.indices) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(com.ifpr.androidapptemplate.R.layout.dialog_edit_task, null)

        val input = dialogView.findViewById<TextInputEditText>(com.ifpr.androidapptemplate.R.id.edit_task_input)
        val btnSave = dialogView.findViewById<View>(com.ifpr.androidapptemplate.R.id.btn_save)
        val btnCancel = dialogView.findViewById<View>(com.ifpr.androidapptemplate.R.id.btn_cancel)
        val btnClose = dialogView.findViewById<ImageView>(com.ifpr.androidapptemplate.R.id.img_close)

        input?.apply {
            setText(tempWeeklyTasks[index])
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val newText = input?.text?.toString()?.trim().orEmpty()
            if (newText.isNotEmpty()) {
                tempWeeklyTasks[index] = newText
                rebuildWeeklyTaskList()
            }
            dialog.dismiss()
        }

        val dismissAction: (View) -> Unit = { dialog.dismiss() }
        btnCancel.setOnClickListener(dismissAction)
        btnClose.setOnClickListener(dismissAction)

        dialog.show()
    }

    // --------- Mensal: adicionar, listar, editar e remover ---------
    private fun addMonthlyTask(text: String) {
        tempMonthlyTasks.add(text)
        rebuildMonthlyTaskList()
    }

    private fun rebuildMonthlyTaskList() {
        val list = binding.monthlyTasksList
        list.removeAllViews()
        tempMonthlyTasks.forEachIndexed { index, t ->
            list.addView(createMonthlyTaskRow(index, t))
        }
        binding.monthlyTasksHeader.isVisible = tempMonthlyTasks.isNotEmpty()
    }

    private fun createMonthlyTaskRow(index: Int, text: String): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        val dotSize = dp(8)
        val dot = View(requireContext()).apply {
            background = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.circle_dot)
            backgroundTintList = ColorStateList.valueOf(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
        }
        val dotParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
            rightMargin = dp(12)
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(dot, dotParams)

        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(com.ifpr.androidapptemplate.R.color.black, null))
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv, tvParams)

        val editBtn = TextView(requireContext()).apply {
            contentDescription = "Editar tarefa"
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val drawable = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.ic_edit_24)
            drawable?.let {
                val size = dp(20)
                it.setBounds(0, 0, size, size)
                it.setTint(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
                setCompoundDrawables(it, null, null, null)
            }
            compoundDrawablePadding = 0
            setOnClickListener { showEditMonthlyTaskDialog(index) }
        }
        row.addView(editBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val delBtn = TextView(requireContext()).apply {
            contentDescription = "Excluir tarefa"
            setPadding(dp(12), dp(8), 0, dp(8))
            val drawable = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.ic_close_24)
            drawable?.let {
                val size = dp(20)
                it.setBounds(0, 0, size, size)
                it.setTint(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
                setCompoundDrawables(it, null, null, null)
            }
            compoundDrawablePadding = 0
            setOnClickListener {
                if (index in tempMonthlyTasks.indices) {
                    tempMonthlyTasks.removeAt(index)
                    rebuildMonthlyTaskList()
                }
            }
        }
        row.addView(delBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return row
    }

    private fun showEditMonthlyTaskDialog(index: Int) {
        if (index !in tempMonthlyTasks.indices) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(com.ifpr.androidapptemplate.R.layout.dialog_edit_task, null)

        val input = dialogView.findViewById<TextInputEditText>(com.ifpr.androidapptemplate.R.id.edit_task_input)
        val btnSave = dialogView.findViewById<View>(com.ifpr.androidapptemplate.R.id.btn_save)
        val btnCancel = dialogView.findViewById<View>(com.ifpr.androidapptemplate.R.id.btn_cancel)
        val btnClose = dialogView.findViewById<ImageView>(com.ifpr.androidapptemplate.R.id.img_close)

        input?.apply {
            setText(tempMonthlyTasks[index])
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val newText = input?.text?.toString()?.trim().orEmpty()
            if (newText.isNotEmpty()) {
                tempMonthlyTasks[index] = newText
                rebuildMonthlyTaskList()
            }
            dialog.dismiss()
        }

        val dismissAction: (View) -> Unit = { dialog.dismiss() }
        btnCancel.setOnClickListener(dismissAction)
        btnClose.setOnClickListener(dismissAction)

        dialog.show()
    }

    // --------- Anual: adicionar, listar, editar e remover ---------
    private fun addYearlyTask(text: String) {
        tempYearlyTasks.add(text)
        rebuildYearlyTaskList()
    }

    private fun rebuildYearlyTaskList() {
        val list = binding.yearlyTasksList
        list.removeAllViews()
        tempYearlyTasks.forEachIndexed { index, t ->
            list.addView(createYearlyTaskRow(index, t))
        }
        binding.yearlyTasksHeader.isVisible = tempYearlyTasks.isNotEmpty()
    }

    private fun createYearlyTaskRow(index: Int, text: String): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        val dotSize = dp(8)
        val dot = View(requireContext()).apply {
            background = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.circle_dot)
            backgroundTintList = ColorStateList.valueOf(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
        }
        val dotParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
            rightMargin = dp(12)
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(dot, dotParams)

        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(com.ifpr.androidapptemplate.R.color.black, null))
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv, tvParams)

        val editBtn = TextView(requireContext()).apply {
            contentDescription = "Editar tarefa"
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val drawable = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.ic_edit_24)
            drawable?.let {
                val size = dp(20)
                it.setBounds(0, 0, size, size)
                it.setTint(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
                setCompoundDrawables(it, null, null, null)
            }
            compoundDrawablePadding = 0
            setOnClickListener { showEditYearlyTaskDialog(index) }
        }
        row.addView(editBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val delBtn = TextView(requireContext()).apply {
            contentDescription = "Excluir tarefa"
            setPadding(dp(12), dp(8), 0, dp(8))
            val drawable = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.ic_close_24)
            drawable?.let {
                val size = dp(20)
                it.setBounds(0, 0, size, size)
                it.setTint(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
                setCompoundDrawables(it, null, null, null)
            }
            compoundDrawablePadding = 0
            setOnClickListener {
                if (index in tempYearlyTasks.indices) {
                    tempYearlyTasks.removeAt(index)
                    rebuildYearlyTaskList()
                }
            }
        }
        row.addView(delBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return row
    }

    private fun showEditYearlyTaskDialog(index: Int) {
        if (index !in tempYearlyTasks.indices) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(com.ifpr.androidapptemplate.R.layout.dialog_edit_task, null)

        val input = dialogView.findViewById<TextInputEditText>(com.ifpr.androidapptemplate.R.id.edit_task_input)
        val btnSave = dialogView.findViewById<View>(com.ifpr.androidapptemplate.R.id.btn_save)
        val btnCancel = dialogView.findViewById<View>(com.ifpr.androidapptemplate.R.id.btn_cancel)
        val btnClose = dialogView.findViewById<ImageView>(com.ifpr.androidapptemplate.R.id.img_close)

        input?.apply {
            setText(tempYearlyTasks[index])
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val newText = input?.text?.toString()?.trim().orEmpty()
            if (newText.isNotEmpty()) {
                tempYearlyTasks[index] = newText
                rebuildYearlyTaskList()
            }
            dialog.dismiss()
        }

        val dismissAction: (View) -> Unit = { dialog.dismiss() }
        btnCancel.setOnClickListener(dismissAction)
        btnClose.setOnClickListener(dismissAction)

        dialog.show()
    }

    private fun addSummaryRow(container: LinearLayout, text: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        val dotSize = dp(8)
        val dot = View(requireContext()).apply {
            background = AppCompatResources.getDrawable(requireContext(), com.ifpr.androidapptemplate.R.drawable.circle_dot)
            backgroundTintList = ColorStateList.valueOf(resources.getColor(com.ifpr.androidapptemplate.R.color.pomotiva_secondary, null))
        }
        val dotParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
            rightMargin = dp(8)
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(dot, dotParams)

        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(com.ifpr.androidapptemplate.R.color.black, null))
            textSize = 14f
            // Retira negrito para manter hierarquia
            setTypeface(typeface, Typeface.NORMAL)
        }
        val tvParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv, tvParams)

        container.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildCyclesSpannable(cycles: Int): CharSequence {
        val label = "Ciclos definidos: "
        val value = cycles.toString()
        val base = label + value
        val ssb = SpannableStringBuilder(base)
        // Bold no título
        ssb.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // Valor agora permanece preto padrão (sem span de cor)
        return ssb
    }

    private fun buildBoldLabel(label: String, value: String): CharSequence {
        val base = label + value
        val ssb = SpannableStringBuilder(base)
        ssb.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return ssb
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}