package com.ifpr.androidapptemplate

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Habilita persistência offline do Realtime Database
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) {
            // Ignora se já foi habilitado em outro ponto
        }
    }
}
