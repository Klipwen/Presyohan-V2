package com.presyohan.app

import android.content.Context
import io.ktor.client.request.*
import io.ktor.http.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull

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
            // Pass name metadata directly to raw_user_meta_data
            // This ensures the database trigger 'handle_new_user' gets the name on creation!
            data = kotlinx.serialization.json.buildJsonObject {
                put("name", name)
            }
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

    suspend fun refreshSessionIfExpired() = withContext(Dispatchers.IO) {
        try {
            val session = client.auth.currentSessionOrNull()
            if (session != null) {
                client.auth.refreshCurrentSession()
                android.util.Log.d("SupabaseAuth", "Successfully verified/refreshed session")
            }
        } catch (e: Exception) {
            android.util.Log.w("SupabaseAuth", "Failed to refresh session: ${e.localizedMessage}")
        }
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

    suspend fun getUserProfile(): AppUserRow? = withContext(Dispatchers.IO) {
        val currentUser = client.auth.currentUserOrNull() ?: return@withContext null
        val uid = currentUser.id

        // 1. Get metadata (always available locally in cached session)
        val metaAny: Any? = currentUser.userMetadata
        val metaName = when (metaAny) {
            is Map<*, *> -> metaAny["name"] as? String ?: (metaAny["full_name"] as? String)
            is JsonObject -> metaAny["name"]?.jsonPrimitive?.contentOrNull ?: metaAny["full_name"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
        val metaAvatar = when (metaAny) {
            is Map<*, *> -> {
                val m = metaAny as Map<*, *>
                (m["avatar_url"] as? String)
                    ?: (m["picture"] as? String)
                    ?: (m["photo_url"] as? String)
                    ?: (m["photoURL"] as? String)
                    ?: (m["image"] as? String)
                    ?: (m["avatar"] as? String)
            }
            is JsonObject -> {
                metaAny["avatar_url"]?.jsonPrimitive?.contentOrNull
                    ?: metaAny["picture"]?.jsonPrimitive?.contentOrNull
                    ?: metaAny["photo_url"]?.jsonPrimitive?.contentOrNull
                    ?: metaAny["photoURL"]?.jsonPrimitive?.contentOrNull
                    ?: metaAny["image"]?.jsonPrimitive?.contentOrNull
                    ?: metaAny["avatar"]?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }

        // 2. Try fetching from database
        try {
            val rows = client.postgrest["app_users"].select {
                filter { eq("id", uid) }
                limit(1)
            }.decodeList<AppUserRow>()
            val dbRow = rows.firstOrNull()
            if (dbRow != null) {
                val mergedName = if (dbRow.name.isNullOrBlank()) metaName else dbRow.name
                val mergedAvatar = if (dbRow.avatar_url.isNullOrBlank()) metaAvatar else dbRow.avatar_url
                return@withContext dbRow.copy(name = mergedName, avatar_url = mergedAvatar)
            }
        } catch (e: Exception) {
            android.util.Log.w("SupabaseAuth", "Failed to fetch profile from DB, falling back to metadata: ${e.localizedMessage}")
        }

        // 3. Fallback to metadata-only row
        return@withContext AppUserRow(
            id = uid,
            name = metaName,
            email = currentUser.email,
            avatar_url = metaAvatar
        )
    }

    // Deprecated wrapper for backward compatibility if used elsewhere
    suspend fun getDisplayName(): String? {
        val profile = getUserProfile()
        return profile?.name
    }

    suspend fun updateUserHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        val uid = client.auth.currentUserOrNull()?.id ?: return@withContext false
        try {
            val isoString = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date())
            client.postgrest["app_users"].update(
                buildJsonObject { put("last_activity_at", isoString) }
            ) {
                filter { eq("id", uid) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isOnboardingCompleted(): Boolean {
        val user = client.auth.currentUserOrNull() ?: return false
        val meta = user.userMetadata ?: return false
        val elem = meta["onboarding_completed"]?.jsonPrimitive
        return elem?.booleanOrNull ?: (elem?.contentOrNull?.toBoolean() ?: false)
    }

    suspend fun setOnboardingCompleted() = withContext(Dispatchers.IO) {
        try {
            val currentMeta = client.auth.currentUserOrNull()?.userMetadata ?: buildJsonObject {}
            val mergedMeta = buildJsonObject {
                currentMeta.forEach { (key, value) ->
                    put(key, value)
                }
                put("onboarding_completed", true)
            }
            client.auth.updateUser {
                data = mergedMeta
            }
        } catch (_: Exception) {}
    }

    suspend fun updateProfile(name: String?, avatarUrl: String?): Boolean = withContext(Dispatchers.IO) {
        val currentUser = client.auth.currentUserOrNull() ?: return@withContext false
        val uid = currentUser.id
        try {
            // Update auth metadata (merging to preserve onboarding / password flags)
            val currentMeta = currentUser.userMetadata ?: buildJsonObject {}
            val mergedMeta = buildJsonObject {
                currentMeta.forEach { (key, value) ->
                    put(key, value)
                }
                if (name != null) put("name", name)
                if (avatarUrl != null) put("avatar_url", avatarUrl)
            }
            client.auth.updateUser {
                data = mergedMeta
            }

            // Update app_users row where auth_uid matches the logged-in user id
            val dbPayload = buildJsonObject {
                if (name != null) put("name", name)
                if (avatarUrl != null) put("avatar_url", avatarUrl)
            }

            client.postgrest["app_users"].update(dbPayload) {
                filter {
                    eq("auth_uid", uid)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadAvatar(bytes: ByteArray, fileExtension: String): String? = withContext(Dispatchers.IO) {
        val currentUser = client.auth.currentUserOrNull() ?: return@withContext null
        val uid = currentUser.id
        val fileName = "$uid-${System.currentTimeMillis()}.$fileExtension"
        try {
            val bucket = client.storage.from("avatars")
            bucket.upload(fileName, bytes) {
                upsert = true
            }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// Made public for header usage
@kotlinx.serialization.Serializable
data class AppUserRow(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val user_code: String? = null,
    val avatar_url: String? = null
)
