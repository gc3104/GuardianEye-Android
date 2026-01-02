package com.example.guardianeye.ui.auth.authcheck

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mpin_store")

class MpinStorage(context: Context) {
    
    private val aead: Aead
    private val dataStore = context.dataStore

    init {
        AeadConfig.register()
        
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "mpin_keyset", "mpin_pref")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://mpin_master_key")
            .build()
            .keysetHandle
            
        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    fun saveMpin(pin: String) {
        val encryptedPin = aead.encrypt(pin.toByteArray(), null)
        val encodedPin = Base64.encodeToString(encryptedPin, Base64.DEFAULT)
        runBlocking {
            dataStore.edit { preferences ->
                preferences[MPIN_KEY] = encodedPin
            }
        }
    }

    fun verifyMpin(pin: String): Boolean {
        val storedPinEncrypted = runBlocking {
            dataStore.data.map { preferences ->
                preferences[MPIN_KEY]
            }.first()
        } ?: return false

        return try {
            val decodedPin = Base64.decode(storedPinEncrypted, Base64.DEFAULT)
            val decryptedPin = String(aead.decrypt(decodedPin, null))
            decryptedPin == pin
        } catch (e: Exception) {
            false
        }
    }

    fun hasMpin(): Boolean {
        return runBlocking {
            dataStore.data.map { preferences ->
                preferences.contains(MPIN_KEY)
            }.first()
        }
    }

    fun deleteMpin() {
        runBlocking {
            dataStore.edit { preferences ->
                preferences.remove(MPIN_KEY)
            }
        }
    }

    companion object {
        private val MPIN_KEY = stringPreferencesKey("mpin")
    }
}