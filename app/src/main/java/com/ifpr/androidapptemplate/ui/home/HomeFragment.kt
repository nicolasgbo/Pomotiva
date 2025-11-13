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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ifpr.androidapptemplate.MainActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import java.util.concurrent.TimeUnit
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.ifpr.androidapptemplate.ui.pomodoro.EndSessionNotificationWorker

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val pomodoroViewModel: PomodoroViewModel by activityViewModels()

    private val channelId = "pomotiva_pomodoro"
    private val notificationId = 1001

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
        ensureNotificationChannel()
        ensureNotificationPermission()
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
            // Reagenda a notificação de término quando o estado muda e está rodando
            val running = pomodoroViewModel.isRunning.value == true
            if (running) {
                scheduleEndNotification()
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
            binding.textDailyCycles.text = "Ciclos diários: $count"
        }
        pomodoroViewModel.isRunning.observe(viewLifecycleOwner) { running ->
            binding.btnStartPause.text = if (running) "PAUSAR" else {
                if (pomodoroViewModel.state.value == PomodoroViewModel.State.PAUSED) "RETOMAR" else "COMEÇAR"
            }
            updateStartPauseStyle()
            if (running) {
                scheduleEndNotification()
            } else {
                cancelEndNotification()
            }
        }
        pomodoroViewModel.notificationEvent.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                sendSystemNotification(msg)
                pomodoroViewModel.consumeNotification()
            }
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

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val descriptionText = "Notificações do Pomodoro"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                val soundUri: Uri = Settings.System.DEFAULT_NOTIFICATION_URI
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(soundUri, attrs)
            }
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendSystemNotification(message: String) {
        val context = requireContext()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingFlags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.pomotiva_logo)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            notify(notificationId, builder.build())
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val context = requireContext()
            val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }
    }

    private fun scheduleEndNotification() {
        val context = requireContext().applicationContext
        val state = pomodoroViewModel.state.value
        val running = pomodoroViewModel.isRunning.value == true
        val remaining = pomodoroViewModel.remainingMillis.value ?: return
        if (!running) return
        if (state == PomodoroViewModel.State.PAUSED || state == PomodoroViewModel.State.IDLE) return

        val message = when (state) {
            PomodoroViewModel.State.WORK -> "Tempo de foco completo!"
            PomodoroViewModel.State.SHORT_BREAK, PomodoroViewModel.State.LONG_BREAK -> "Pausa finalizada! Volte ao foco!"
            else -> return
        }

        val data = workDataOf(
            EndSessionNotificationWorker.KEY_MESSAGE to message
        )
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<EndSessionNotificationWorker>()
            .setInitialDelay(remaining, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EndSessionNotificationWorker.UNIQUE_WORK,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelEndNotification() {
        val context = requireContext().applicationContext
        WorkManager.getInstance(context).cancelUniqueWork(EndSessionNotificationWorker.UNIQUE_WORK)
    }
}