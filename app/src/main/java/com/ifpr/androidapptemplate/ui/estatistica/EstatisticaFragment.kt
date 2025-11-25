package com.ifpr.androidapptemplate.ui.estatistica

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ifpr.androidapptemplate.databinding.FragmentEstatisticaBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ifpr.androidapptemplate.R
import androidx.lifecycle.lifecycleScope
import com.ifpr.androidapptemplate.ui.pomodoro.DailyStats
import com.ifpr.androidapptemplate.ui.pomodoro.Goals
import com.ifpr.androidapptemplate.ui.pomodoro.PomodoroRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EstatisticaFragment : Fragment() {

    private var _binding: FragmentEstatisticaBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val repository = PomodoroRepository()
    private var statsJob: Job? = null
    private var goalsJob: Job? = null
    private var currentPeriodDays: Int = 1
    private var lastDailyMap: Map<String, DailyStats> = emptyMap()
    private var lastGoals: Goals = Goals()
    private var lastTodayCycles: Long = 0L
    private var periodTarget: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEstatisticaBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Ajusta apenas o padding inferior para gestos/botões do sistema.
        // O padding superior da Toolbar já é aplicado no XML via paddingTop=?attr/actionBarSize
        val originalTopPadding = binding.scrollRoot.paddingTop
        val originalBottomPadding = binding.scrollRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollRoot) { v, insets ->
            val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(
                top = originalTopPadding,
                bottom = originalBottomPadding + nb.bottom
            )
            insets
        }

        setupChips()
        observeGoals()
        observeAndRender(periodDays = 1)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class Period(val days: Int) { HOJE(1), SEMANA(7), MES(30), ANO(365) }

    private fun setupChips() {
        binding.chipHoje.isChecked = true
        binding.chipHoje.setOnClickListener { observeAndRender(Period.HOJE.days) }
        binding.chipSemana.setOnClickListener { observeAndRender(Period.SEMANA.days) }
        binding.chipMes.setOnClickListener { observeAndRender(Period.MES.days) }
        binding.chipAno.setOnClickListener { observeAndRender(Period.ANO.days) }
    }

    private fun observeAndRender(periodDays: Int) {
        statsJob?.cancel()
        statsJob = viewLifecycleOwner.lifecycleScope.launch {
            currentPeriodDays = periodDays
            repositoryDailyRange(periodDays).collectLatest { map ->
                lastDailyMap = map
                renderKpis(map)
                renderMetaCards(map)
                renderChart(map)
                renderMeta2ProgressIfPossible()
                renderCompletionKpiIfPossible()
            }
        }
        // Atualiza o target do período ao trocar o chip
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val prefix = periodPrefix(currentPeriodDays)
                if (prefix != null) {
                    val pd = repository.fetchPeriod(prefix)
                    periodTarget = pd.cycles_target
                    renderMeta2ProgressIfPossible()
                    renderCompletionKpiIfPossible()
                }
            } catch (_: Exception) { }
        }
    }

    private fun repositoryDailyRange(days: Int) = repositoryDailyRangeImpl(days)

    private fun repositoryDailyRangeImpl(days: Int) = kotlinx.coroutines.flow.callbackFlow<Map<String, DailyStats>> {
        val u = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (u == null) {
            trySend(emptyMap()); close(); return@callbackFlow
        }
        val ref = com.google.firebase.database.FirebaseDatabase.getInstance()
            .reference.child("users").child(u).child("pomodoro").child("stats").child("daily")
            .orderByKey()
            .limitToLast(days)
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val map = mutableMapOf<String, DailyStats>()
                for (child in snapshot.children) {
                    val key = child.key ?: continue
                    val s = child.getValue(DailyStats::class.java) ?: DailyStats()
                    map[key] = s
                }
                trySend(map.toSortedMap())
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) { }
        }
        val dbref = (ref as com.google.firebase.database.Query)
        dbref.addValueEventListener(listener)
        awaitClose { dbref.removeEventListener(listener) }
    }

    private fun renderKpis(dailyMap: Map<String, DailyStats>) {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val today = dailyMap[todayKey] ?: DailyStats()
        val focusMin = (today.focus_ms / 60000L).toInt()
        binding.tvKpiTempoValor.text = "$focusMin min"
        binding.tvKpiSessoesValor.text = today.cycles.toString()
    }

    private fun renderCompletionKpiIfPossible() {
        // Soma de ciclos do período selecionado (mapa já limitado por currentPeriodDays)
        val totalCyclesInPeriod = lastDailyMap.values.sumOf { it.cycles }
        val target = periodTarget
        val pct = if (target > 0L) {
            ((totalCyclesInPeriod.toDouble() * 100.0) / target.toDouble()).coerceIn(0.0, 100.0)
        } else 0.0
        binding.tvKpiTaxaValor.text = String.format(Locale.getDefault(), "%.0f%%", pct)
    }

    private fun renderMetaCards(dailyMap: Map<String, DailyStats>) {
        // Soma do período atual
        val totalFocusMin = (dailyMap.values.sumOf { it.focus_ms } / 60000L).toInt()
        val totalCycles = dailyMap.values.sumOf { it.cycles }.toInt()

        // Meta 1: apenas quantidade de minutos focados no período (sem comparação)
        binding.tvMeta1Titulo.text = "Tempo focado ${periodLabel(currentPeriodDays)}"
        binding.tvMeta1Valores.text = "$totalFocusMin min"
        binding.progressMeta1.apply {
            visibility = View.VISIBLE
            isIndeterminate = false
            max = 100
            setProgressCompat(100, false)
        }

        // Meta 2 permanece vinculada às metas. Texto exibe total do período.
        binding.tvMeta2Titulo.text = "Sessões ${periodLabel(currentPeriodDays)}"
        binding.tvMeta2Valores.text = "$totalCycles sessões"
    }

    private fun observeGoals() {
        goalsJob?.cancel()
        goalsJob = viewLifecycleOwner.lifecycleScope.launch {
            repository.dailyStatsTodayFlow().collectLatest { today ->
                lastTodayCycles = today.cycles
                renderMeta2ProgressIfPossible()
            }
        }
    }

    private fun renderMeta2ProgressIfPossible() {
        // X: ciclos de hoje (sem somar entre dias)
        val totalCyclesToday = lastTodayCycles
        // Y: meta específica do período selecionado (sem multiplicar por dias)
        val target = periodTarget
        // Atualiza texto X/Y (exibe 0 se não houver meta definida)
        binding.tvMeta2Valores.text = "$totalCyclesToday/$target sessões"
        // Atualiza progresso percentual com guarda contra zero
        val denom = if (target > 0L) target else 1L
        val pct = (((totalCyclesToday * 100L) / denom).toInt()).coerceIn(0, 100)
        binding.progressMeta2.max = 100
        binding.progressMeta2.setProgressCompat(pct, true)
    }

    private fun periodPrefix(days: Int): String? = when (days) {
        1 -> "daily"
        7 -> "weekly"
        30 -> "monthly"
        365 -> "yearly"
        else -> null
    }

    private fun periodLabel(days: Int): String = when (days) {
        1 -> "diário"
        7 -> "semanal"
        30 -> "mensal"
        365 -> "anual"
        else -> "no período"
    }

    private fun renderChart(dailyMap: Map<String, DailyStats>) {
        val chart = binding.chartPrincipal
        val keys = dailyMap.keys.toList()
        val labels = keys.map { key ->
            val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfOut = SimpleDateFormat("dd/MM", Locale.getDefault())
            try { sdfOut.format(sdfIn.parse(key) ?: Date()) } catch (_: Exception) { key }
        }
        val entries = keys.mapIndexed { i, k ->
            val s = dailyMap[k] ?: DailyStats()
            BarEntry(i.toFloat(), (s.focus_ms / 60000f))
        }

        val dataSet = BarDataSet(entries, getString(R.string.estat_chart_title)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.pomotiva_secondary)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.black)
            valueTextSize = 10f
        }
        val data = BarData(dataSet).apply { barWidth = 0.5f }

        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false

            axisLeft.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.black)
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.purple_200)
                gridColor = ContextCompat.getColor(requireContext(), R.color.purple_200)
                granularity = 10f
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                setDrawGridLines(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.black)
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.purple_200)
                granularity = 1f
                labelCount = labels.size
            }

            this.data = data
            setFitBars(true)
            animateY(600)
            invalidate()
        }
    }
}