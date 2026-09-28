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
    // Fallback for local unit test environments only; production credentials must be injected at runtime
    private const val TEST_LOCAL_URL = "http://127.0.0.1:54321"
    private const val TEST_LOCAL_KEY = "test-anon-key"

    @Volatile
    private var instance: SupabaseClient? = null

    fun getOrCreate(
        url: String = TEST_LOCAL_URL,
        anonKey: String = TEST_LOCAL_KEY
    ): SupabaseClient {
        require(!url.contains("placeholder", ignoreCase = true)) {
            "SUPABASE_URL cannot contain placeholder: $url"
        }
        require(!anonKey.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.anon")) {
            "SUPABASE_ANON_KEY cannot use placeholder JWT token"
        }

        return instance ?: synchronized(this) {
            instance ?: createSupabaseClient(
                supabaseUrl = url.ifBlank { TEST_LOCAL_URL },
                supabaseKey = anonKey.ifBlank { TEST_LOCAL_KEY }
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
