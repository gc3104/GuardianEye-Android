package com.example.guardianeye.ui.auth.register

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RegisterViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _registrationError = MutableStateFlow<String?>(null)
    val registrationError: StateFlow<String?> = _registrationError.asStateFlow()

    fun register(email: String, password: String, confirmPassword: String, onSuccess: () -> Unit) {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            _registrationError.value = "Please fill in all fields"
            return
        }

        if (password != confirmPassword) {
            _registrationError.value = "Passwords do not match"
            return
        }

        _isLoading.value = true
        _registrationError.value = null

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    updateFCMToken()
                    onSuccess()
                } else {
                    _registrationError.value = "Registration failed: ${task.exception?.message}"
                }
            }
    }
    
    fun clearError() {
        _registrationError.value = null
    }

    private fun updateFCMToken() {
        val userId = auth.currentUser?.uid ?: return
        val tokenRef = db.collection("tokens").document(userId)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val newToken = task.result
                val tokenData = hashMapOf("device_token" to newToken)
                tokenRef.set(tokenData)
            }
        }
    }
}