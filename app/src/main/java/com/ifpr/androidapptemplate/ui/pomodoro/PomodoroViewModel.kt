package com.ifpr.androidapptemplate.ui.pomodoro

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PomodoroViewModel : ViewModel() {

    enum class State { IDLE, WORK, SHORT_BREAK, LONG_BREAK, PAUSED }

    // Durations in milliseconds
    private val workDuration = 25L * 60 * 1000
    private val shortBreakDuration = 5L * 60 * 1000
    private val longBreakDuration = 15L * 60 * 1000
    private val longBreakInterval = 4 // long break after 4 work sessions

    private val _remainingMillis = MutableLiveData<Long>(workDuration)
    val remainingMillis: LiveData<Long> = _remainingMillis

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> = _state

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _cycleCount = MutableLiveData(0)
    val cycleCount: LiveData<Int> = _cycleCount

    // Total duration of the current session (millis) to drive progress UI
    private val _totalMillis = MutableLiveData<Long>(workDuration)
    val totalMillis: LiveData<Long> = _totalMillis

    private var currentDuration = workDuration
    private var timer: CountDownTimer? = null

    fun startWork() {
        startTimer(State.WORK, workDuration)
    }

    fun startShortBreak() {
        startTimer(State.SHORT_BREAK, shortBreakDuration)
    }

    fun startLongBreak() {
        startTimer(State.LONG_BREAK, longBreakDuration)
    }

    private fun startTimer(newState: State, duration: Long) {
        cancelTimer()
        _state.value = newState
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
                if (nextCycle % longBreakInterval == 0) {
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
            cancelTimer()
            _state.value = State.PAUSED
            _isRunning.value = false
        }
    }

    fun resume() {
        if (_state.value == State.PAUSED) {
            startTimer(State.WORK, _remainingMillis.value ?: currentDuration)
        }
    }

    fun toggleStartPause() {
        if (_isRunning.value == true) {
            pause()
        } else {
            when (_state.value) {
                State.IDLE, State.SHORT_BREAK, State.LONG_BREAK -> startWork()
                State.PAUSED -> resume()
                State.WORK -> startWork()
                else -> startWork()
            }
        }
    }

    fun reset() {
        cancelTimer()
        _state.value = State.IDLE
        _isRunning.value = false
        _remainingMillis.value = workDuration
        _totalMillis.value = workDuration
        currentDuration = workDuration
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
                    _remainingMillis.value = longBreakDuration
                } else {
                    _state.value = State.SHORT_BREAK
                    _remainingMillis.value = shortBreakDuration
                }
            }
            State.SHORT_BREAK, State.LONG_BREAK, State.IDLE, State.PAUSED -> {
                _state.value = State.WORK
                _remainingMillis.value = workDuration
                _totalMillis.value = workDuration
            }
            else -> {}
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelTimer()
    }
}
