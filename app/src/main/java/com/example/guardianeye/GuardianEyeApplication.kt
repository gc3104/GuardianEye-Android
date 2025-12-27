package com.example.guardianeye

import android.app.Application
import com.google.firebase.FirebaseApp

class GuardianEyeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}