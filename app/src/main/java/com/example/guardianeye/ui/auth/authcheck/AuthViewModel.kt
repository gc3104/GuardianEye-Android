package com.example.guardianeye.ui.auth.authcheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val mpinStorage = MpinStorage(application)
    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.CHECKING)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun checkAuthStatus() {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _authState.value = AuthState.NOT_LOGGED_IN
                return@launch
            }

            if (mpinStorage.hasMpin()) {
                _authState.value = AuthState.MPIN_REQUIRED
            } else {
                _authState.value = AuthState.SET_MPIN_REQUIRED
            }
        }
    }
    
    fun verifyMpin(mpin: String): Boolean {
        val correct = mpinStorage.verifyMpin(mpin)
        if (correct) {
            _authState.value = AuthState.AUTHENTICATED
        }
        return correct
    }
    
    fun setMpin(mpin: String) {
        mpinStorage.saveMpin(mpin)
        _authState.value = AuthState.AUTHENTICATED
    }
    
    fun onBiometricSuccess() {
        _authState.value = AuthState.AUTHENTICATED
    }
    
    fun onLoginSuccess() {
        checkAuthStatus()
    }
}

enum class AuthState {
    CHECKING,
    NOT_LOGGED_IN,
    MPIN_REQUIRED,
    SET_MPIN_REQUIRED,
    AUTHENTICATED
}