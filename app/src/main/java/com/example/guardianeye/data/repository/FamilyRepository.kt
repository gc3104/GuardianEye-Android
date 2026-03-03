package com.example.guardianeye.data.repository

import android.util.Log
import com.example.guardianeye.model.Family
import com.example.guardianeye.model.FamilyMember
import com.example.guardianeye.model.MemberRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FamilyRepository"

    fun getFamilyFlow(): Flow<Family?> = callbackFlow {
        val userId = auth.currentUser?.uid ?: run {
            trySend(null)
            return@callbackFlow
        }

        // First find which family the user belongs to
        val userFamilyRef = db.collection("users").document(userId)
        
        val listener = userFamilyRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val familyId = snapshot?.getString("familyId")
            if (familyId == null) {
                trySend(null)
                return@addSnapshotListener
            }

            // Then listen to that family
            db.collection("families").document(familyId)
                .addSnapshotListener { familySnapshot, familyError ->
                    if (familyError != null) {
                        return@addSnapshotListener
                    }
                    val family = familySnapshot?.toObject(Family::class.java)
                    trySend(family)
                }
        }

        awaitClose { listener.remove() }
    }

    suspend fun createFamily(name: String): String? {
        val userId = auth.currentUser?.uid ?: return null
        val familyId = db.collection("families").document().id
        
        val family = Family(
            id = familyId,
            name = name,
            adminId = userId,
            members = listOf(
                FamilyMember(userId = userId, role = MemberRole.ADMIN)
            )
        )

        return try {
            db.runTransaction { transaction ->
                transaction.set(db.collection("families").document(familyId), family)
                transaction.update(db.collection("users").document(userId), "familyId", familyId)
            }.await()
            familyId
        } catch (e: Exception) {
            Log.e(TAG, "Error creating family", e)
            null
        }
    }

    suspend fun inviteMember(email: String): Boolean {
        // In a real app, this would involve cloud functions to send email/notification
        // and add a pending invitation. For now, we'll simulate adding by email if user exists.
        return try {
            val userSnapshot = db.collection("users").whereEqualTo("email", email).get().await()
            if (userSnapshot.isEmpty) return false
            
            val userId = userSnapshot.documents[0].id
            val currentFamilyId = getCurrentFamilyId() ?: return false
            
            db.collection("families").document(currentFamilyId).update(
                "members", com.google.firebase.firestore.FieldValue.arrayUnion(
                    FamilyMember(userId = userId, role = MemberRole.MEMBER)
                )
            ).await()
            
            db.collection("users").document(userId).update("familyId", currentFamilyId).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inviting member", e)
            false
        }
    }

    private suspend fun getCurrentFamilyId(): String? {
        val userId = auth.currentUser?.uid ?: return null
        return db.collection("users").document(userId).get().await().getString("familyId")
    }
}
