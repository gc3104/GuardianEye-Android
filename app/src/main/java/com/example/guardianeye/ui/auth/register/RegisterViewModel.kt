package com.example.guardianeye.ui.auth.register

import androidx.lifecycle.ViewModel
import com.example.guardianeye.data.repository.FirebaseManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val firebaseManager: FirebaseManager
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

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
                    // Centralized token update
                    firebaseManager.updateFCMToken()
                    onSuccess()
                } else {
                    _registrationError.value = "Registration failed: ${task.exception?.message}"
                }
            }
    }
    
    fun clearError() {
        _registrationError.value = null
    }
}
