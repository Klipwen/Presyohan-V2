package com.project.presyohan

import android.content.Context
import io.github.jan.supabase.auth.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SupabaseAuthService {
    private val client get() = SupabaseProvider.client

    suspend fun signInEmail(email: String, password: String) = withContext(Dispatchers.IO) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        true
    }

    // Google OAuth via Custom Tabs / deep link
    suspend fun signInWithGoogle(context: Context, scheme: String = "presyohan://auth-callback") = withContext(Dispatchers.IO) {
        try {
            client.auth.openAuthFlow(Google) {
                // Scheme must match intent-filter in AndroidManifest
                this.scheme = scheme
                this.context = context
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun signUpEmail(name: String, email: String, password: String) = withContext(Dispatchers.IO) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // Set name in user metadata for quick header display
        try {
            client.auth.updateUser {
                data = mapOf("name" to name)
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

    // Helper to fetch display name for headers: prefer auth metadata, fallback to app_users
    suspend fun getDisplayName(): String? = withContext(Dispatchers.IO) {
        val metaName = client.auth.currentUserOrNull()?.userMetadata?.get("name") as? String
        if (!metaName.isNullOrBlank()) return@withContext metaName

        val uid = client.auth.currentUserOrNull()?.id ?: return@withContext null
        return@withContext try {
            val row = client.postgrest["app_users"].select {
                filter { eq("id", uid) }
                limit(1)
            }.decodeSingleOrNull<AppUserRow>()
            row?.name
        } catch (_: Exception) { null }
    }
}

@kotlinx.serialization.Serializable
private data class AppUserRow(
    val id: String,
    val name: String? = null,
    val email: String? = null
)