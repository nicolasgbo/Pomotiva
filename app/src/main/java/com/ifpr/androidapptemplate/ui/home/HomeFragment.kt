package com.ifpr.androidapptemplate.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ifpr.androidapptemplate.databinding.FragmentHomeBinding
import com.ifpr.androidapptemplate.ui.pomodoro.PomodoroViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var pomodoroViewModel: PomodoroViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        pomodoroViewModel = ViewModelProvider(this).get(PomodoroViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

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
                PomodoroViewModel.State.IDLE -> "Pronto"
            }
            // Atualiza label do botão start/pause conforme estado
            binding.btnStartPause.text = if (pomodoroViewModel.isRunning.value == true) "Pause" else when (state) {
                PomodoroViewModel.State.PAUSED -> "Resume"
                else -> "Start"
            }
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
        }
        pomodoroViewModel.isRunning.observe(viewLifecycleOwner) { running ->
            binding.btnStartPause.text = if (running) "Pause" else {
                if (pomodoroViewModel.state.value == PomodoroViewModel.State.PAUSED) "Resume" else "Start"
            }
        }
    }

    private fun setupClicks() {
        binding.btnStartPause.setOnClickListener { pomodoroViewModel.toggleStartPause() }
        binding.btnReset.setOnClickListener { pomodoroViewModel.reset() }
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
        // Custom arc view draws progress fractionally with animation
        binding.pomoProgress.setProgressByMillis(totalMillis, remainingMillis, animate = true)
    }
}