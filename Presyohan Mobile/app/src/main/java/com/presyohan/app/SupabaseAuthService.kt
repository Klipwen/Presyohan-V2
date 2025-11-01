package com.presyohan.app

import android.content.Context
import io.ktor.client.request.*
import io.ktor.http.*
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

    // Resend signup verification email (Supabase REST: POST /auth/v1/resend)
    suspend fun resendSignupEmail(email: String): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        if (baseUrl.isBlank() || anonKey.isBlank()) throw RuntimeException("Supabase is not configured")

        val url = "$baseUrl/auth/v1/resend"
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)
        try {
            val response = http.post(url) {
                header("apikey", anonKey)
                header(io.ktor.http.HttpHeaders.Authorization, "Bearer $anonKey")
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody("{" +
                    "\"type\":\"signup\"," +
                    "\"email\":\"$email\"" +
                    "}")
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            throw RuntimeException(e.localizedMessage ?: "Failed to resend verification email")
        } finally {
            http.close()
        }
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