package com.fitter.shared.auth

import io.github.jan.supabase.postgrest.postgrest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SupabaseClientFactoryTest {

    @AfterTest
    fun tearDown() {
        SupabaseClientFactory.resetForTesting()
    }

    @Test
    fun testPostgrestDefaultSchemaIsFitter() {
        SupabaseClientFactory.resetForTesting()
        val client = SupabaseClientFactory.getOrCreate()
        assertEquals("fitter", client.postgrest.config.defaultSchema)
    }
}
