package com.fitter.shared.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientFactory {
    // Default placeholder for local/test environments; overridden at runtime via PlatformConfig
    const val DEFAULT_SUPABASE_URL = "http://127.0.0.1:54321"
    const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.anon"

    @Volatile
    private var instance: SupabaseClient? = null

    fun getOrCreate(
        url: String = DEFAULT_SUPABASE_URL,
        anonKey: String = DEFAULT_ANON_KEY
    ): SupabaseClient {
        return instance ?: synchronized(this) {
            instance ?: createSupabaseClient(
                supabaseUrl = url.ifBlank { DEFAULT_SUPABASE_URL },
                supabaseKey = anonKey.ifBlank { DEFAULT_ANON_KEY }
            ) {
                install(Auth) {
                    sessionManager = try {
                        SettingsSessionManager()
                    } catch (_: Throwable) {
                        MemorySessionManager()
                    }
                    codeVerifierCache = try {
                        SettingsCodeVerifierCache()
                    } catch (_: Throwable) {
                        MemoryCodeVerifierCache()
                    }
                    enableLifecycleCallbacks = try {
                        kotlinx.coroutines.Dispatchers.Main.isDispatchNeeded(kotlin.coroutines.EmptyCoroutineContext)
                        true
                    } catch (_: Throwable) {
                        false
                    }
                }
                install(Postgrest) {
                    defaultSchema = "fitter"
                }
                install(Realtime)
            }.also { instance = it }
        }
    }

    fun setInstance(client: SupabaseClient) {
        instance = client
    }

    fun resetForTesting() {
        instance = null
    }
}
