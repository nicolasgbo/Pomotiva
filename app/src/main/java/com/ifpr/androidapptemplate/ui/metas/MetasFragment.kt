package com.ifpr.androidapptemplate.ui.metas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import com.ifpr.androidapptemplate.ui.pomodoro.Goals
import com.ifpr.androidapptemplate.ui.pomodoro.PomodoroRepository
import com.ifpr.androidapptemplate.ui.pomodoro.GoalsUiHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.Lifecycle

class MetasFragment : Fragment() {

    private var _binding: FragmentMetasBinding? = null
    private val binding get() = _binding!!
    // Lista temporária das tarefas diárias (antes de persistir)
    private val tempDailyTasks = mutableListOf<String>()
    // Listas temporárias para semanal, mensal e anual
    private val tempWeeklyTasks = mutableListOf<String>()
    private val tempMonthlyTasks = mutableListOf<String>()
    private val tempYearlyTasks = mutableListOf<String>()

    // Listas atuais carregadas do BD para reabrir formulário com dados existentes
    private val currentDailyTasks = mutableListOf<String>()
    private val currentWeeklyTasks = mutableListOf<String>()
    private val currentMonthlyTasks = mutableListOf<String>()
    private val currentYearlyTasks = mutableListOf<String>()
    // Chaves dos itens no Realtime DB para exclusão precisa
    private val currentDailyTaskKeys = mutableListOf<String>()
    private val currentWeeklyTaskKeys = mutableListOf<String>()
    private val currentMonthlyTaskKeys = mutableListOf<String>()
    private val currentYearlyTaskKeys = mutableListOf<String>()

    // Integração com metas no Realtime Database
    private lateinit var repository: PomodoroRepository
    private var currentGoals: Goals = Goals()
    private val goalsUiHelper = GoalsUiHelper

    // Flags para evitar fechar o formulário enquanto o usuário edita
    private var isEditingDaily = false
    private var isEditingWeekly = false
    private var isEditingMonthly = false
    private var isEditingYearly = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val metasViewModel =
            ViewModelProvider(this, MetasViewModel.factory()).get(MetasViewModel::class.java)

        _binding = FragmentMetasBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Repo via ViewModel (DI simples)
        repository = metasViewModel.repository

        // Carrega metas existentes do usuário e preenche UI respeitando ciclo de vida
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.goalsFlow().collectLatest { goals ->
                    currentGoals = goals
                    if (!isEditingDaily) {
                        if (goals.daily_cycles_target > 0) {
                            binding.inputDailyCycles.setText(goals.daily_cycles_target.toString())
                        } else {
                            binding.inputDailyCycles.setText("")
                        }
                    }
                    if (goals.daily_cycles_target > 0) {
                        binding.summaryDailyCycles.text = buildCyclesSpannable(goals.daily_cycles_target.toInt())
                        if (!isEditingDaily) {
                            binding.summaryDailyCard.isVisible = true
                            binding.formDailyContainer.isGone = true
                            binding.btnAddDaily.apply {
                                text = "Editar meta"
                                setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24)
                            }
                        }
                    }
                }
            }
        }

        // Pré-carrega resumos por período (sequencial para reduzir leituras paralelas)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val p = repository.fetchPeriod("daily")
                if (!isEditingDaily) {
                    val has = (p.cycles_target > 0L) || (p.created_at > 0L)
                    binding.summaryDailyTasksList.removeAllViews()
                    currentDailyTasks.clear(); currentDailyTasks.addAll(p.tasks.values)
                    currentDailyTaskKeys.clear(); currentDailyTaskKeys.addAll(p.tasks.keys)
                    if (p.tasks.isEmpty()) addSummaryRow(binding.summaryDailyTasksList, "(Sem tarefas)") else p.tasks.values.forEach { addSummaryRow(binding.summaryDailyTasksList, it) }
                    if (has) {
                        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                        if (p.created_at > 0L) binding.summaryDailyDate.text = buildBoldLabel("Data de criação: ", fmt.format(java.util.Date(p.created_at)))
                        if (p.cycles_target > 0L) binding.summaryDailyCycles.text = buildCyclesSpannable(p.cycles_target.toInt()) else binding.summaryDailyCycles.text = ""
                        binding.summaryDailyCard.isVisible = true
                        binding.formDailyContainer.isGone = true
                        binding.btnAddDaily.apply { text = "Editar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24) }
                    } else {
                        binding.summaryDailyCard.isGone = true
                        binding.btnAddDaily.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }
                    }
                }
            } catch (_: Exception) { }

            try {
                val p = repository.fetchPeriod("weekly")
                if (!isEditingWeekly) {
                    val has = (p.cycles_target > 0L) || (p.created_at > 0L)
                    binding.summaryWeeklyTasksList.removeAllViews()
                    currentWeeklyTasks.clear(); currentWeeklyTasks.addAll(p.tasks.values)
                    currentWeeklyTaskKeys.clear(); currentWeeklyTaskKeys.addAll(p.tasks.keys)
                    if (p.tasks.isEmpty()) addSummaryRow(binding.summaryWeeklyTasksList, "(Sem tarefas)") else p.tasks.values.forEach { addSummaryRow(binding.summaryWeeklyTasksList, it) }
                    if (has) {
                        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                        if (p.created_at > 0L) binding.summaryWeeklyDate.text = buildBoldLabel("Data de criação: ", fmt.format(java.util.Date(p.created_at)))
                        if (p.cycles_target > 0L) binding.summaryWeeklyCycles.text = buildCyclesSpannable(p.cycles_target.toInt()) else binding.summaryWeeklyCycles.text = ""
                        binding.summaryWeeklyCard.isVisible = true
                        binding.formWeeklyContainer.isGone = true
                        binding.btnAddWeekly.apply { text = "Editar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24) }
                    } else {
                        binding.summaryWeeklyCard.isGone = true
                        binding.btnAddWeekly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }
                    }
                }
            } catch (_: Exception) { }

            try {
                val p = repository.fetchPeriod("monthly")
                if (!isEditingMonthly) {
                    val has = (p.cycles_target > 0L) || (p.created_at > 0L)
                    binding.summaryMonthlyTasksList.removeAllViews()
                    currentMonthlyTasks.clear(); currentMonthlyTasks.addAll(p.tasks.values)
                    currentMonthlyTaskKeys.clear(); currentMonthlyTaskKeys.addAll(p.tasks.keys)
                    if (p.tasks.isEmpty()) addSummaryRow(binding.summaryMonthlyTasksList, "(Sem tarefas)") else p.tasks.values.forEach { addSummaryRow(binding.summaryMonthlyTasksList, it) }
                    if (has) {
                        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                        if (p.created_at > 0L) binding.summaryMonthlyDate.text = buildBoldLabel("Data de criação: ", fmt.format(java.util.Date(p.created_at)))
                        if (p.cycles_target > 0L) binding.summaryMonthlyCycles.text = buildCyclesSpannable(p.cycles_target.toInt()) else binding.summaryMonthlyCycles.text = ""
                        binding.summaryMonthlyCard.isVisible = true
                        binding.formMonthlyContainer.isGone = true
                        binding.btnAddMonthly.apply { text = "Editar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24) }
                    } else {
                        binding.summaryMonthlyCard.isGone = true
                        binding.btnAddMonthly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }
                    }
                }
            } catch (_: Exception) { }

            try {
                val p = repository.fetchPeriod("yearly")
                if (!isEditingYearly) {
                    val has = (p.cycles_target > 0L) || (p.created_at > 0L)
                    binding.summaryYearlyTasksList.removeAllViews()
                    currentYearlyTasks.clear(); currentYearlyTasks.addAll(p.tasks.values)
                    currentYearlyTaskKeys.clear(); currentYearlyTaskKeys.addAll(p.tasks.keys)
                    if (p.tasks.isEmpty()) addSummaryRow(binding.summaryYearlyTasksList, "(Sem tarefas)") else p.tasks.values.forEach { addSummaryRow(binding.summaryYearlyTasksList, it) }
                    if (has) {
                        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                        if (p.created_at > 0L) binding.summaryYearlyDate.text = buildBoldLabel("Data de criação: ", fmt.format(java.util.Date(p.created_at)))
                        if (p.cycles_target > 0L) binding.summaryYearlyCycles.text = buildCyclesSpannable(p.cycles_target.toInt()) else binding.summaryYearlyCycles.text = ""
                        binding.summaryYearlyCard.isVisible = true
                        binding.formYearlyContainer.isGone = true
                        binding.btnAddYearly.apply { text = "Editar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_edit_24) }
                    } else {
                        binding.summaryYearlyCard.isGone = true
                        binding.btnAddYearly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }
                    }
                }
            } catch (_: Exception) { }

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
            // Preenche o formulário com as tarefas já salvas
            if (!form.isVisible) {
                val u = FirebaseAuth.getInstance().currentUser
                if (u == null) {
                    Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val period = repository.fetchPeriod("daily")
                        goalsUiHelper.onPrefill(period) { tasks ->
                            tempDailyTasks.clear()
                            tempDailyTasks.addAll(tasks)
                            currentDailyTasks.clear()
                            currentDailyTasks.addAll(tasks)
                            currentDailyTaskKeys.clear()
                            currentDailyTaskKeys.addAll(period.tasks.keys)
                            rebuildDailyTaskList()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Falha ao carregar metas", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // Mantém o formulário aberto (não fecha ao re-clicar)
            form.isVisible = true
            isEditingDaily = true
        }

        // Adicionar tarefa diária à lista temporária e à UI (persistindo individualmente via repository)
        binding.btnAddDailyTask.setOnClickListener {
            val text = binding.inputDailyTask.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Digite uma tarefa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addDailyTask(text)
            binding.inputDailyTask.setText("")
            viewLifecycleOwner.lifecycleScope.launch {
                isEditingDaily = true
                try {
                    val key = repository.addTask("daily", text)
                    if (key != null) {
                        currentDailyTasks.add(text)
                        currentDailyTaskKeys.add(key)
                    } else {
                        Toast.makeText(requireContext(), "Falha ao adicionar tarefa", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao adicionar tarefa", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingDaily = false
                }
            }
        }

        // Salvar meta diária e mostrar resumo (usar repository.setCycles e ensureCreatedAt)
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
            if (tempDailyTasks.isEmpty()) {
                Toast.makeText(requireContext(), "Sem tarefas!", Toast.LENGTH_SHORT).show()
            }

            viewLifecycleOwner.lifecycleScope.launch {
                isEditingDaily = true
                try {
                    repository.setCycles("daily", cycles.toLong())
                    repository.ensureCreatedAt("daily")
                    currentGoals = currentGoals.copy(daily_cycles_target = cycles.toLong())
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao salvar meta", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingDaily = false
                }
            }
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

            // Excluir meta diária no DB (ciclos, created_at e tasks) via repository
            viewLifecycleOwner.lifecycleScope.launch {
                isEditingDaily = true
                try {
                    repository.deletePeriod("daily")
                    currentDailyTasks.clear(); currentDailyTaskKeys.clear()
                    binding.summaryDailyCard.isGone = true
                    binding.formDailyContainer.isGone = true
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao excluir meta", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingDaily = false
                }
            }
        }
        // --------- Semanal ---------
        binding.btnAddWeekly.setOnClickListener {
            val form = binding.formWeeklyContainer
            if (binding.summaryWeeklyCard.isVisible) binding.summaryWeeklyCard.isGone = true
            if (!form.isVisible) {
                val u = FirebaseAuth.getInstance().currentUser
                if (u == null) {
                    Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val period = repository.fetchPeriod("weekly")
                        tempWeeklyTasks.clear()
                        tempWeeklyTasks.addAll(period.tasks.values)
                        currentWeeklyTasks.clear(); currentWeeklyTasks.addAll(period.tasks.values)
                        currentWeeklyTaskKeys.clear(); currentWeeklyTaskKeys.addAll(period.tasks.keys)
                        rebuildWeeklyTaskList()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Falha ao carregar metas", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // Mantém o formulário aberto
            form.isVisible = true
            isEditingWeekly = true
        }
        binding.btnAddWeeklyTask.setOnClickListener {
            val text = binding.inputWeeklyTask.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) { Toast.makeText(requireContext(), "Digite uma tarefa semanal", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            addWeeklyTask(text); binding.inputWeeklyTask.setText("")
            viewLifecycleOwner.lifecycleScope.launch {
                isEditingWeekly = true
                try {
                    val key = repository.addTask("weekly", text)
                    if (key != null) {
                        currentWeeklyTasks.add(text)
                        currentWeeklyTaskKeys.add(key)
                    } else {
                        Toast.makeText(requireContext(), "Falha ao adicionar tarefa", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao adicionar tarefa", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingWeekly = false
                }
            }
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
            if (tempWeeklyTasks.isEmpty()) { Toast.makeText(requireContext(), "Sem tarefas!", Toast.LENGTH_SHORT).show() }

            viewLifecycleOwner.lifecycleScope.launch {
                isEditingWeekly = true
                try {
                    repository.setCycles("weekly", cycles.toLong())
                    repository.ensureCreatedAt("weekly")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao salvar meta", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingWeekly = false
                }
            }
        }
        binding.btnCancelWeekly.setOnClickListener {
            tempWeeklyTasks.clear(); binding.weeklyTasksList.removeAllViews(); binding.weeklyTasksHeader.isVisible = false
            binding.inputWeeklyCycles.setText(""); binding.inputWeeklyTask.setText("")
            binding.summaryWeeklyCard.isGone = true; binding.formWeeklyContainer.isGone = true
            binding.btnAddWeekly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }

            viewLifecycleOwner.lifecycleScope.launch {
                isEditingWeekly = true
                try {
                    repository.deletePeriod("weekly")
                    currentWeeklyTasks.clear(); currentWeeklyTaskKeys.clear()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao excluir meta", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingWeekly = false
                }
            }
        }

        // --------- Mensal ---------
        binding.btnAddMonthly.setOnClickListener {
            val form = binding.formMonthlyContainer
            if (binding.summaryMonthlyCard.isVisible) binding.summaryMonthlyCard.isGone = true
            if (!form.isVisible) {
                val u = FirebaseAuth.getInstance().currentUser
                if (u == null) {
                    Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val period = repository.fetchPeriod("monthly")
                        tempMonthlyTasks.clear(); tempMonthlyTasks.addAll(period.tasks.values)
                        currentMonthlyTasks.clear(); currentMonthlyTasks.addAll(period.tasks.values)
                        currentMonthlyTaskKeys.clear(); currentMonthlyTaskKeys.addAll(period.tasks.keys)
                        rebuildMonthlyTaskList()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Falha ao carregar metas", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // Mantém o formulário aberto
            form.isVisible = true
            isEditingMonthly = true
        }
        binding.btnAddMonthlyTask.setOnClickListener {
            val text = binding.inputMonthlyTask.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) { Toast.makeText(requireContext(), "Digite uma tarefa mensal", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            addMonthlyTask(text); binding.inputMonthlyTask.setText("")
            viewLifecycleOwner.lifecycleScope.launch {
                isEditingMonthly = true
                try {
                    val key = repository.addTask("monthly", text)
                    if (key != null) {
                        currentMonthlyTasks.add(text)
                        currentMonthlyTaskKeys.add(key)
                    } else {
                        Toast.makeText(requireContext(), "Falha ao adicionar tarefa", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao adicionar tarefa", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingMonthly = false
                }
            }
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
            if (tempMonthlyTasks.isEmpty()) {
                Toast.makeText(requireContext(), "Sem tarefas!", Toast.LENGTH_SHORT).show()
            }

            viewLifecycleOwner.lifecycleScope.launch {
                isEditingMonthly = true
                try {
                    repository.setCycles("monthly", cycles.toLong())
                    repository.ensureCreatedAt("monthly")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao salvar meta", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingMonthly = false
                }
            }
        }
        binding.btnCancelMonthly.setOnClickListener {
            tempMonthlyTasks.clear(); binding.monthlyTasksList.removeAllViews(); binding.monthlyTasksHeader.isVisible = false
            binding.inputMonthlyCycles.setText(""); binding.inputMonthlyTask.setText("")
            binding.summaryMonthlyCard.isGone = true; binding.formMonthlyContainer.isGone = true
            binding.btnAddMonthly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }

            viewLifecycleOwner.lifecycleScope.launch {
                isEditingMonthly = true
                try {
                    repository.deletePeriod("monthly")
                    currentMonthlyTasks.clear(); currentMonthlyTaskKeys.clear()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao excluir meta", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingMonthly = false
                }
            }
        }

        // --------- Anual ---------
        binding.btnAddYearly.setOnClickListener {
            val form = binding.formYearlyContainer
            if (binding.summaryYearlyCard.isVisible) binding.summaryYearlyCard.isGone = true
            if (!form.isVisible) {
                val u = FirebaseAuth.getInstance().currentUser
                if (u == null) { Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val period = repository.fetchPeriod("yearly")
                        tempYearlyTasks.clear(); tempYearlyTasks.addAll(period.tasks.values)
                        currentYearlyTasks.clear(); currentYearlyTasks.addAll(period.tasks.values)
                        currentYearlyTaskKeys.clear(); currentYearlyTaskKeys.addAll(period.tasks.keys)
                        rebuildYearlyTaskList()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Falha ao carregar metas", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // Mantém o formulário aberto
            form.isVisible = true
            isEditingYearly = true
        }
        binding.btnAddYearlyTask.setOnClickListener {
            val text = binding.inputYearlyTask.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) { Toast.makeText(requireContext(), "Digite uma tarefa anual", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            addYearlyTask(text); binding.inputYearlyTask.setText("")
            viewLifecycleOwner.lifecycleScope.launch {
                isEditingYearly = true
                try {
                    val key = repository.addTask("yearly", text)
                    if (key != null) {
                        currentYearlyTasks.add(text)
                        currentYearlyTaskKeys.add(key)
                    } else {
                        Toast.makeText(requireContext(), "Falha ao adicionar tarefa", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao adicionar tarefa", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingYearly = false
                }
            }
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
            if (tempYearlyTasks.isEmpty()) {
                Toast.makeText(requireContext(), "Sem tarefas!", Toast.LENGTH_SHORT).show()
            }

            viewLifecycleOwner.lifecycleScope.launch {
                isEditingYearly = true
                try {
                    repository.setCycles("yearly", cycles.toLong())
                    repository.ensureCreatedAt("yearly")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao salvar meta", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingYearly = false
                }
            }
        }
        binding.btnCancelYearly.setOnClickListener {
            tempYearlyTasks.clear(); binding.yearlyTasksList.removeAllViews(); binding.yearlyTasksHeader.isVisible = false
            binding.inputYearlyCycles.setText(""); binding.inputYearlyTask.setText("")
            binding.summaryYearlyCard.isGone = true; binding.formYearlyContainer.isGone = true
            binding.btnAddYearly.apply { text = "Adicionar meta"; setIconResource(com.ifpr.androidapptemplate.R.drawable.ic_add_24) }

            viewLifecycleOwner.lifecycleScope.launch {
                isEditingYearly = true
                try {
                    repository.deletePeriod("yearly")
                    currentYearlyTasks.clear(); currentYearlyTaskKeys.clear()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Erro ao excluir meta", Toast.LENGTH_SHORT).show()
                } finally {
                    isEditingYearly = false
                }
            }
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
                    if (index in currentDailyTaskKeys.indices) {
                        val key = currentDailyTaskKeys[index]
                        val u = FirebaseAuth.getInstance().currentUser
                        if (u == null) {
                            Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    repository.removeTask("daily", key)
                                    currentDailyTaskKeys.removeAt(index)
                                    if (index < currentDailyTasks.size) currentDailyTasks.removeAt(index)
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Erro ao excluir tarefa", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
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
                // Atualiza no Realtime Database
                val u = FirebaseAuth.getInstance().currentUser
                if (u == null) {
                    Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                } else {
                    if (index in currentDailyTaskKeys.indices) {
                        val key = currentDailyTaskKeys[index]
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                repository.updateTask("daily", key, newText)
                                if (index < currentDailyTasks.size) currentDailyTasks[index] = newText
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Erro ao atualizar tarefa", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
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
                    // Remover via repositório
                    if (index in currentWeeklyTaskKeys.indices) {
                        val key = currentWeeklyTaskKeys[index]
                        val u = FirebaseAuth.getInstance().currentUser
                        if (u == null) {
                            Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    repository.removeTask("weekly", key)
                                    currentWeeklyTaskKeys.removeAt(index)
                                    if (index < currentWeeklyTasks.size) currentWeeklyTasks.removeAt(index)
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Erro ao excluir tarefa", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
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
                // Atualiza via repositório
                if (index in currentWeeklyTaskKeys.indices) {
                    val key = currentWeeklyTaskKeys[index]
                    val u = FirebaseAuth.getInstance().currentUser
                    if (u == null) {
                        Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                    } else {
                        viewLifecycleOwner.lifecycleScope.launch {
                            try { repository.updateTask("weekly", key, newText) } catch (e: Exception) { Toast.makeText(requireContext(), "Erro ao atualizar tarefa", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
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
                    if (index in currentMonthlyTaskKeys.indices) {
                        val key = currentMonthlyTaskKeys[index]
                        val u = FirebaseAuth.getInstance().currentUser
                        if (u == null) {
                            Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    repository.removeTask("monthly", key)
                                    currentMonthlyTaskKeys.removeAt(index)
                                    if (index < currentMonthlyTasks.size) currentMonthlyTasks.removeAt(index)
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Erro ao excluir tarefa", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
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
                if (index in currentMonthlyTaskKeys.indices) {
                    val key = currentMonthlyTaskKeys[index]
                    val u = FirebaseAuth.getInstance().currentUser
                    if (u == null) {
                        Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                    } else {
                        viewLifecycleOwner.lifecycleScope.launch {
                            try { repository.updateTask("monthly", key, newText) } catch (e: Exception) { Toast.makeText(requireContext(), "Erro ao atualizar tarefa", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
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
                    if (index in currentYearlyTaskKeys.indices) {
                        val key = currentYearlyTaskKeys[index]
                        val u = FirebaseAuth.getInstance().currentUser
                        if (u == null) {
                            Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    repository.removeTask("yearly", key)
                                    currentYearlyTaskKeys.removeAt(index)
                                    if (index < currentYearlyTasks.size) currentYearlyTasks.removeAt(index)
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Erro ao excluir tarefa", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
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
                if (index in currentYearlyTaskKeys.indices) {
                    val key = currentYearlyTaskKeys[index]
                    val u = FirebaseAuth.getInstance().currentUser
                    if (u == null) {
                        Toast.makeText(requireContext(), "Faça login para editar metas", Toast.LENGTH_SHORT).show()
                    } else {
                        viewLifecycleOwner.lifecycleScope.launch {
                            try { repository.updateTask("yearly", key, newText) } catch (e: Exception) { Toast.makeText(requireContext(), "Erro ao atualizar tarefa", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
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