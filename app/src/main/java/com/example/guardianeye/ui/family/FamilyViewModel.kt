package com.example.guardianeye.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardianeye.data.repository.FamilyRepository
import com.example.guardianeye.model.Family
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository
) : ViewModel() {

    val familyState: StateFlow<Family?> = familyRepository.getFamilyFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun createFamily(name: String) {
        viewModelScope.launch {
            familyRepository.createFamily(name)
        }
    }

    fun inviteMember(email: String) {
        viewModelScope.launch {
            familyRepository.inviteMember(email)
        }
    }
}
