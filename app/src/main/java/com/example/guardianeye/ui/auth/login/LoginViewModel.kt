package com.example.guardianeye.ui.auth.login

import androidx.lifecycle.ViewModel
import com.example.guardianeye.data.repository.FirebaseManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val firebaseManager: FirebaseManager
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

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
                    // Centralized token update
                    firebaseManager.updateFCMToken()
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
}
