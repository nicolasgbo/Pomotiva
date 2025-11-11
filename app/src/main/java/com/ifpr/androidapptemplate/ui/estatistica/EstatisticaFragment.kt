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

class EstatisticaFragment : Fragment() {

    private var _binding: FragmentEstatisticaBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

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

        setupChipsDefault()
        setupKpisMock()
        setupMetasMock()
        setupBarChartMock(period = Period.HOJE)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class Period { HOJE, SEMANA, MES, ANO }

    private fun setupChipsDefault() {
        // Seleciona Hoje por padrão
        binding.chipHoje.isChecked = true

        binding.chipHoje.setOnClickListener { setupBarChartMock(Period.HOJE) }
        binding.chipSemana.setOnClickListener { setupBarChartMock(Period.SEMANA) }
        binding.chipMes.setOnClickListener { setupBarChartMock(Period.MES) }
        binding.chipAno.setOnClickListener { setupBarChartMock(Period.ANO) }
    }

    private fun setupKpisMock() {
        binding.tvKpiTempoValor.text = "320 min"
        binding.tvKpiSessoesValor.text = "6"
        binding.tvKpiTaxaValor.text = "85%"
    }

    private fun setupMetasMock() {
        // Meta 1: 480/600 min => 80%
        binding.tvMeta1Titulo.text = "Tempo focado semanal"
        binding.tvMeta1Valores.text = "480/600 min  •  Falta 120 min"
        binding.progressMeta1.max = 100
        binding.progressMeta1.setProgressCompat(80, true)

        // Meta 2: 22/30 sessões => ~73%
        binding.tvMeta2Titulo.text = "Sessões por mês"
        binding.tvMeta2Valores.text = "22/30 sessões  •  Falta 8"
        binding.progressMeta2.max = 100
        binding.progressMeta2.setProgressCompat(73, true)
    }

    private fun setupBarChartMock(period: Period) {
        val chart = binding.chartPrincipal

        // Dados mock por período
        val (entries, labels) = when (period) {
            Period.HOJE -> {
                // Horas do dia (amostragem 0..23 com alguns valores)
                val hrs = listOf(8, 9, 10, 11, 14, 15, 16, 20)
                val vals = listOf(25f, 30f, 20f, 15f, 40f, 35f, 30f, 20f)
                val e = hrs.mapIndexed { i, h -> BarEntry(i.toFloat(), vals[i]) }
                val l = hrs.map { String.format("%02dh", it) }
                e to l
            }
            Period.SEMANA -> {
                val vals = listOf(45f, 60f, 30f, 50f, 70f, 80f, 40f)
                val e = vals.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
                val l = listOf("S", "T", "Q", "Q", "S", "S", "D")
                e to l
            }
            Period.MES -> {
                // Agrupa por 5 dias para melhorar a legibilidade (resulta em 6 barras: 1-5, 6-10, ..., 26-30)
                val dailyVals = List(30) { (20..80).random().toFloat() }
                val bucketSize = 5
                val chunks = dailyVals.chunked(bucketSize)

                val e = chunks.mapIndexed { i, chunk ->
                    BarEntry(i.toFloat(), chunk.sum())
                }
                val l = chunks.mapIndexed { i, _ ->
                    val start = i * bucketSize + 1
                    val end = minOf((i + 1) * bucketSize, dailyVals.size)
                    "$start-$end"
                }
                e to l
            }
            Period.ANO -> {
                val vals = listOf(120f, 130f, 150f, 160f, 170f, 140f, 150f, 180f, 190f, 175f, 160f, 155f)
                val e = vals.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
                val l = listOf("JAN","FEV","MAR","ABR","MAI","JUN","JUL","AGO","SET","OUT","NOV","DEZ")
                e to l
            }
        }

        val dataSet = BarDataSet(entries, getString(R.string.estat_chart_title)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.pomotiva_secondary)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.black)
            valueTextSize = 10f
        }
        val data = BarData(dataSet).apply {
            barWidth = 0.5f
        }

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