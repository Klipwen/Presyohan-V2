package com.presyohan.app

import android.content.Context
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

object SupabaseAuthService {
    private val client get() = SupabaseProvider.client

    suspend fun signInEmail(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            // Only succeed if a session exists (email may require verification)
            client.auth.currentSessionOrNull() != null
        } catch (e: Exception) {
            throw RuntimeException(e.localizedMessage ?: "Supabase email sign-in failed")
        }
    }

    // Native Google Sign-In: exchange ID token with Supabase (no browser)
    suspend fun signInWithGoogleIdToken(idToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            client.auth.signInWith(IDToken) {
                provider = Google
                this.idToken = idToken
            }
            client.auth.currentSessionOrNull() != null
        } catch (e: Exception) {
            // Surface a clearer message upstream
            throw RuntimeException(e.localizedMessage ?: "Supabase ID token exchange failed")
        }
    }

    // OAuth (browser) can be added later if needed; mobile uses native GoogleSignIn

    suspend fun signUpEmail(name: String, email: String, password: String) = withContext(Dispatchers.IO) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // Set name in user metadata for quick header display
        try {
            client.auth.updateUser {
                data = kotlinx.serialization.json.buildJsonObject {
                    put("name", name)
                }
            }
        } catch (_: Exception) { /* ignore */ }

        // Insert into public.app_users table
        try {
            val userId = client.auth.currentUserOrNull()?.id
            if (userId != null) {
                client.postgrest["app_users"].insert(
                    mapOf(
                        "id" to userId,
                        "name" to name,
                        "email" to email
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore if table/policy not ready; we can add later
        }
        true
    }

    fun isLoggedIn(): Boolean {
        return client.auth.currentSessionOrNull() != null
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        client.auth.signOut()
    }

    // Non-suspend helper for immediate display name (auth metadata/email only)
    fun getDisplayNameImmediate(): String {
        val metaAny: Any? = client.auth.currentUserOrNull()?.userMetadata
        val metaName = when (metaAny) {
            is Map<*, *> -> metaAny["name"] as? String
            is JsonObject -> metaAny["name"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
        if (!metaName.isNullOrBlank()) return metaName
        return client.auth.currentUserOrNull()?.email ?: ""
    }

    // Helper to fetch display name for headers: prefer auth metadata, fallback to app_users
    suspend fun getDisplayName(): String? = withContext(Dispatchers.IO) {
        val metaAny: Any? = client.auth.currentUserOrNull()?.userMetadata
        val metaName = when (metaAny) {
            is Map<*, *> -> metaAny["name"] as? String
            is JsonObject -> metaAny["name"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
        if (!metaName.isNullOrBlank()) return@withContext metaName

        val uid = client.auth.currentUserOrNull()?.id ?: return@withContext null
        return@withContext try {
            val rows = client.postgrest["app_users"].select {
                filter { eq("id", uid) }
                limit(1)
            }.decodeList<AppUserRow>()
            rows.firstOrNull()?.name
        } catch (_: Exception) { null }
    }
}

@kotlinx.serialization.Serializable
private data class AppUserRow(
    val id: String,
    val name: String? = null,
    val email: String? = null
)