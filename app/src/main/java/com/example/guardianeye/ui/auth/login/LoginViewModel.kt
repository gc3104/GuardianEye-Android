package com.example.guardianeye.ui.auth.login

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoginViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()
    
    private val _resetPasswordMessage = MutableStateFlow<String?>(null)
    val resetPasswordMessage: StateFlow<String?> = _resetPasswordMessage.asStateFlow()

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isEmpty() || password.isEmpty()) {
            _loginError.value = "Please enter email and password"
            return
        }

        _isLoading.value = true
        _loginError.value = null
        
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    updateFCMToken()
                    onSuccess()
                } else {
                    _loginError.value = "Authentication failed: ${task.exception?.message}"
                }
            }
    }
    
    fun resetPassword(email: String) {
        if (email.isEmpty()) {
            _resetPasswordMessage.value = "Please enter your email first"
            return
        }
        
        _isLoading.value = true
        _resetPasswordMessage.value = null
        
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    _resetPasswordMessage.value = "Password reset email sent."
                } else {
                    _resetPasswordMessage.value = "Failed to send reset email: ${task.exception?.message}"
                }
            }
    }
    
    fun clearError() {
        _loginError.value = null
    }
    
    fun clearMessage() {
        _resetPasswordMessage.value = null
    }

    private fun updateFCMToken() {
        val userId = auth.currentUser?.uid ?: return
        val tokenRef = db.collection("tokens").document(userId)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val newToken = task.result
                tokenRef.get().addOnSuccessListener { document ->
                    if (!document.exists() || document.getString("device_token") != newToken) {
                        val tokenData = hashMapOf("device_token" to newToken)
                        tokenRef.set(tokenData)
                    }
                }
            }
        }
    }
}