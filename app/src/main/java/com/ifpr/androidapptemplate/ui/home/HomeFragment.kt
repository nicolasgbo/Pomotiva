package com.ifpr.androidapptemplate.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ifpr.androidapptemplate.databinding.FragmentHomeBinding
import com.ifpr.androidapptemplate.ui.pomodoro.PomodoroViewModel
import androidx.core.content.ContextCompat
import com.ifpr.androidapptemplate.R

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val pomodoroViewModel: PomodoroViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Garante que a aba Foco inicie selecionada
        if (pomodoroViewModel.state.value == PomodoroViewModel.State.IDLE) {
            pomodoroViewModel.selectWork()
        }

        setupObservers()
        setupClicks()
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupObservers() {
        pomodoroViewModel.state.observe(viewLifecycleOwner) { state ->
            binding.textState.text = when (state) {
                PomodoroViewModel.State.WORK -> "Foco"
                PomodoroViewModel.State.SHORT_BREAK -> "Pausa curta"
                PomodoroViewModel.State.LONG_BREAK -> "Pausa longa"
                PomodoroViewModel.State.PAUSED -> "Pausado"
                PomodoroViewModel.State.IDLE -> ""
            }
            // Atualiza texto da sessão atual
            val sessionLabel = when (state) {
                PomodoroViewModel.State.WORK -> "Foco"
                PomodoroViewModel.State.SHORT_BREAK -> "Pausa curta"
                PomodoroViewModel.State.LONG_BREAK -> "Pausa longa"
                PomodoroViewModel.State.PAUSED -> "Pausado"
                PomodoroViewModel.State.IDLE -> ""
            }
            binding.textCurrentSession.text = if (sessionLabel.isNotEmpty()) "Sessão atual: $sessionLabel" else ""
            // Atualiza label do botão start/pause conforme estado
            binding.btnStartPause.text = if (pomodoroViewModel.isRunning.value == true) "PAUSAR" else when (state) {
                PomodoroViewModel.State.PAUSED -> "RETOMAR"
                else -> "COMEÇAR"
            }
            updateModeTabs()
            updateStartPauseStyle()
        }
        pomodoroViewModel.remainingMillis.observe(viewLifecycleOwner) { millis ->
            binding.textTimer.text = formatMillis(millis)
        }
        pomodoroViewModel.totalMillis.observe(viewLifecycleOwner) { total ->
            val remaining = pomodoroViewModel.remainingMillis.value ?: total
            updateProgress(total, remaining)
        }
        // Also update when remaining changes to animate progress
        pomodoroViewModel.remainingMillis.observe(viewLifecycleOwner) { remaining ->
            val total = pomodoroViewModel.totalMillis.value ?: 1L
            updateProgress(total, remaining)
        }
        pomodoroViewModel.cycleCount.observe(viewLifecycleOwner) { count ->
            binding.textCycle.text = "Ciclo: $count"
            binding.textDailyCycles.text = "Ciclos diários: $count"
        }
        pomodoroViewModel.isRunning.observe(viewLifecycleOwner) { running ->
            binding.btnStartPause.text = if (running) "PAUSAR" else {
                if (pomodoroViewModel.state.value == PomodoroViewModel.State.PAUSED) "RETOMAR" else "COMEÇAR"
            }
            updateStartPauseStyle()
        }
    }

    private fun setupClicks() {
        binding.btnStartPause.setOnClickListener {
            // Estado alvo após o clique (se não estava rodando, passará a rodar)
            val targetRunning = pomodoroViewModel.isRunning.value != true
            pomodoroViewModel.toggleStartPause()

            // Aplica imediatamente o estilo correspondente ao estado alvo
            val drawableRes = if (targetRunning) R.drawable.bg_pomodoro_button_outline else R.drawable.bg_pomodoro_button
            binding.btnStartPause.setBackgroundResource(drawableRes)
            binding.btnStartPause.backgroundTintList = null
            val textColor = if (targetRunning)
                ContextCompat.getColor(requireContext(), R.color.pomotiva_secondary)
            else
                ContextCompat.getColor(requireContext(), R.color.white)
            binding.btnStartPause.setTextColor(textColor)
        }
        // Tabs de modo (apenas selecionam o modo, não iniciam o timer)
        binding.tabFocus.setOnClickListener {
            pomodoroViewModel.selectWork()
            updateModeTabs()
            updateStartPauseStyle()
        }
        binding.tabShort.setOnClickListener {
            pomodoroViewModel.selectShortBreak()
            updateModeTabs()
            updateStartPauseStyle()
        }
        binding.tabLong.setOnClickListener {
            pomodoroViewModel.selectLongBreak()
            updateModeTabs()
            updateStartPauseStyle()
        }
        // Botões ocultos (mantidos por compatibilidade)
        binding.btnNext.setOnClickListener { pomodoroViewModel.next() }
        binding.btnWork.setOnClickListener { pomodoroViewModel.startWork() }
        binding.btnShortBreak.setOnClickListener { pomodoroViewModel.startShortBreak() }
        binding.btnLongBreak.setOnClickListener { pomodoroViewModel.startLongBreak() }
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    private fun updateProgress(totalMillis: Long, remainingMillis: Long) {
        // Atualiza o arco de progresso de forma animada
        binding.pomoProgress.setProgressByMillis(totalMillis, remainingMillis, animate = true)
    }

    private fun updateStartPauseStyle() {
        val running = pomodoroViewModel.isRunning.value == true
        val drawableRes = if (running) R.drawable.bg_pomodoro_button_outline else R.drawable.bg_pomodoro_button
        binding.btnStartPause.setBackgroundResource(drawableRes)
        binding.btnStartPause.backgroundTintList = null
        val textColor = if (running)
            ContextCompat.getColor(requireContext(), R.color.pomotiva_secondary)
        else
            ContextCompat.getColor(requireContext(), R.color.white)
        binding.btnStartPause.setTextColor(textColor)
    }

    private fun updateModeTabs() {
        val state = pomodoroViewModel.state.value
        val selectedBg = R.drawable.bg_tab_selected
        val selectedTextColor = ContextCompat.getColor(requireContext(), R.color.white)
        val unselectedTextColor = ContextCompat.getColor(requireContext(), R.color.pomotiva_secondary)

        // Focus
        if (state == PomodoroViewModel.State.WORK) {
            binding.tabFocus.setBackgroundResource(selectedBg)
            binding.textTabFocus.setTextColor(selectedTextColor)
            binding.underlineFocus.visibility = View.GONE
        } else {
            binding.tabFocus.setBackgroundResource(0)
            binding.textTabFocus.setTextColor(unselectedTextColor)
            binding.underlineFocus.visibility = View.VISIBLE
        }

        // Short Break
        if (state == PomodoroViewModel.State.SHORT_BREAK) {
            binding.tabShort.setBackgroundResource(selectedBg)
            binding.textTabShort.setTextColor(selectedTextColor)
            binding.underlineShort.visibility = View.GONE
        } else {
            binding.tabShort.setBackgroundResource(0)
            binding.textTabShort.setTextColor(unselectedTextColor)
            binding.underlineShort.visibility = View.VISIBLE
        }

        // Long Break
        if (state == PomodoroViewModel.State.LONG_BREAK) {
            binding.tabLong.setBackgroundResource(selectedBg)
            binding.textTabLong.setTextColor(selectedTextColor)
            binding.underlineLong.visibility = View.GONE
        } else {
            binding.tabLong.setBackgroundResource(0)
            binding.textTabLong.setTextColor(unselectedTextColor)
            binding.underlineLong.visibility = View.VISIBLE
        }
    }
}