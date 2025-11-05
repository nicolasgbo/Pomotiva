package com.ifpr.androidapptemplate.ui.pomodoro

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PomodoroViewModel : ViewModel() {

    enum class State { IDLE, WORK, SHORT_BREAK, LONG_BREAK, PAUSED }

    // Durations in milliseconds (mutáveis para permitir personalização)
    private var workDurationMs = 25L * 60 * 1000
    private var shortBreakDurationMs = 5L * 60 * 1000
    private var longBreakDurationMs = 15L * 60 * 1000
    companion object {
        private const val LONG_BREAK_INTERVAL = 4 // long break after 4 work sessions
    }

    private val _remainingMillis = MutableLiveData<Long>(workDurationMs)
    val remainingMillis: LiveData<Long> = _remainingMillis

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> = _state

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _cycleCount = MutableLiveData(0)
    val cycleCount: LiveData<Int> = _cycleCount

    // Total duration of the current session (millis) to drive progress UI
    private val _totalMillis = MutableLiveData<Long>(workDurationMs)
    val totalMillis: LiveData<Long> = _totalMillis

    private var currentDuration = workDurationMs
    private var timer: CountDownTimer? = null
    private var lastActiveState: State = State.WORK

    private fun setMode(state: State, durationMs: Long) {
        cancelTimer()
        _state.value = state
        lastActiveState = state
        currentDuration = durationMs
        _remainingMillis.value = durationMs
        _totalMillis.value = durationMs
        _isRunning.value = false
    }

    // Seleciona modo sem iniciar o timer
    fun selectWork() = setMode(State.WORK, workDurationMs)

    fun selectShortBreak() = setMode(State.SHORT_BREAK, shortBreakDurationMs)

    fun selectLongBreak() = setMode(State.LONG_BREAK, longBreakDurationMs)

    fun startWork() {
        startTimer(State.WORK, workDurationMs)
    }

    fun startShortBreak() {
        startTimer(State.SHORT_BREAK, shortBreakDurationMs)
    }

    fun startLongBreak() {
        startTimer(State.LONG_BREAK, longBreakDurationMs)
    }

    private fun startTimer(newState: State, duration: Long) {
        cancelTimer()
        _state.value = newState
        lastActiveState = newState
        currentDuration = duration
        _remainingMillis.value = duration
        _totalMillis.value = duration
        _isRunning.value = true
        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingMillis.postValue(millisUntilFinished)
            }
            override fun onFinish() {
                _isRunning.postValue(false)
                _remainingMillis.postValue(0)
                onTimerFinished()
            }
        }.start()
    }

    private fun onTimerFinished() {
        when (_state.value) {
            State.WORK -> {
                val nextCycle = (_cycleCount.value ?: 0) + 1
                _cycleCount.postValue(nextCycle)
                if (nextCycle % LONG_BREAK_INTERVAL == 0) {
                    startLongBreak()
                } else {
                    startShortBreak()
                }
            }
            State.SHORT_BREAK, State.LONG_BREAK -> {
                startWork()
            }
            else -> { /* no-op */ }
        }
    }

    fun pause() {
        if (_isRunning.value == true) {
            val prev = _state.value ?: State.WORK
            cancelTimer()
            lastActiveState = prev
            _state.value = State.PAUSED
            _isRunning.value = false
        }
    }

    fun resume() {
        if (_state.value == State.PAUSED) {
            val remaining = _remainingMillis.value ?: currentDuration
            val resumeState = lastActiveState
            // Não reseta totalMillis; apenas retoma o timer com o restante
            cancelTimer()
            _state.value = resumeState
            _isRunning.value = true
            timer = object : CountDownTimer(remaining, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    _remainingMillis.postValue(millisUntilFinished)
                }
                override fun onFinish() {
                    _isRunning.postValue(false)
                    _remainingMillis.postValue(0)
                    onTimerFinished()
                }
            }.start()
        }
    }

    fun toggleStartPause() {
        if (_isRunning.value == true) {
            pause()
        } else {
            when (_state.value) {
                State.PAUSED -> resume()
                State.SHORT_BREAK -> startShortBreak()
                State.LONG_BREAK -> startLongBreak()
                State.WORK -> startWork()
                State.IDLE -> startWork()
                else -> startWork()
            }
        }
    }

    fun reset() {
        cancelTimer()
        _state.value = State.IDLE
        _isRunning.value = false
        _remainingMillis.value = workDurationMs
        _totalMillis.value = workDurationMs
        currentDuration = workDurationMs
    }

    fun next() {
        cancelTimer()
        _isRunning.value = false
        when (_state.value) {
            State.WORK -> {
                val nextCycle = (_cycleCount.value ?: 0) + 1
                _cycleCount.value = nextCycle
                if (nextCycle % longBreakInterval == 0) {
                    _state.value = State.LONG_BREAK
                    _remainingMillis.value = longBreakDurationMs
                } else {
                    _state.value = State.SHORT_BREAK
                    _remainingMillis.value = shortBreakDurationMs
                }
            }
            State.SHORT_BREAK, State.LONG_BREAK, State.IDLE, State.PAUSED -> {
                _state.value = State.WORK
                _remainingMillis.value = workDurationMs
                _totalMillis.value = workDurationMs
            }
            else -> {}
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    // Atualiza durações (minutos) e aplica imediatamente no estado atual
    fun setDurations(workMin: Int, shortBreakMin: Int, longBreakMin: Int) {
        workDurationMs = workMin.toLong() * 60 * 1000
        shortBreakDurationMs = shortBreakMin.toLong() * 60 * 1000
        longBreakDurationMs = longBreakMin.toLong() * 60 * 1000

        val running = _isRunning.value == true
        val currentState = _state.value ?: State.WORK

        if (running) {
            // Reinicia imediatamente a sessão atual com a nova duração
            when (currentState) {
                State.WORK -> startWork()
                State.SHORT_BREAK -> startShortBreak()
                State.LONG_BREAK -> startLongBreak()
                State.PAUSED -> { // se pausado, retoma com o novo total no modo anterior
                    _state.value = lastActiveState
                    when (lastActiveState) {
                        State.WORK -> startWork()
                        State.SHORT_BREAK -> startShortBreak()
                        State.LONG_BREAK -> startLongBreak()
                        else -> startWork()
                    }
                }
                else -> startWork()
            }
        } else {
            // Não rodando: sincroniza os valores exibidos conforme o modo atual
            when (currentState) {
                State.WORK, State.IDLE -> {
                    _remainingMillis.value = workDurationMs
                    _totalMillis.value = workDurationMs
                    currentDuration = workDurationMs
                }
                State.SHORT_BREAK -> {
                    _remainingMillis.value = shortBreakDurationMs
                    _totalMillis.value = shortBreakDurationMs
                    currentDuration = shortBreakDurationMs
                }
                State.LONG_BREAK -> {
                    _remainingMillis.value = longBreakDurationMs
                    _totalMillis.value = longBreakDurationMs
                    currentDuration = longBreakDurationMs
                }
                State.PAUSED -> {
                    // Se pausado, aplica ao modo anterior
                    when (lastActiveState) {
                        State.WORK, State.IDLE -> {
                            _remainingMillis.value = workDurationMs
                            _totalMillis.value = workDurationMs
                            currentDuration = workDurationMs
                        }
                        State.SHORT_BREAK -> {
                            _remainingMillis.value = shortBreakDurationMs
                            _totalMillis.value = shortBreakDurationMs
                            currentDuration = shortBreakDurationMs
                        }
                        State.LONG_BREAK -> {
                            _remainingMillis.value = longBreakDurationMs
                            _totalMillis.value = longBreakDurationMs
                            currentDuration = longBreakDurationMs
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelTimer()
    }
}
