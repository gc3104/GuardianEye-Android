package com.example.guardianeye.ui.auth.authcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val mpinStorage: MpinStorage
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow(AuthState.CHECKING)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

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
    
    fun verifyMpin(mpin: String) {
        viewModelScope.launch {
            if (mpinStorage.verifyMpin(mpin)) {
                _authState.value = AuthState.AUTHENTICATED
            } else {
                _errorEvent.emit("Incorrect MPIN")
            }
        }
    }
    
    fun setMpin(mpin: String) {
        viewModelScope.launch {
            mpinStorage.saveMpin(mpin)
            _authState.value = AuthState.AUTHENTICATED
        }
    }
    
    fun onBiometricSuccess() {
        _authState.value = AuthState.AUTHENTICATED
    }
}

enum class AuthState {
    CHECKING,
    NOT_LOGGED_IN,
    MPIN_REQUIRED,
    SET_MPIN_REQUIRED,
    AUTHENTICATED
}
