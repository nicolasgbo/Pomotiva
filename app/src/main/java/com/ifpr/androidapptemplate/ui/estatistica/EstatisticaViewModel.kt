package com.ifpr.androidapptemplate.ui.estatistica

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class EstatisticaViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Esta é a tela de Estatística"
    }
    val text: LiveData<String> = _text
}