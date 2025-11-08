package com.ifpr.androidapptemplate.ui.metas

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MetasViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is metas Fragment"
    }
    val text: LiveData<String> = _text
}