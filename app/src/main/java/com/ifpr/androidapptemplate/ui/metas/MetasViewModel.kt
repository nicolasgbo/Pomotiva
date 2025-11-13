package com.ifpr.androidapptemplate.ui.metas

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ifpr.androidapptemplate.ui.pomodoro.PomodoroRepository

class MetasViewModel(val repository: PomodoroRepository) : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is metas Fragment"
    }
    val text: LiveData<String> = _text

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = PomodoroRepository()
                @Suppress("UNCHECKED_CAST")
                return MetasViewModel(repo) as T
            }
        }
    }
}