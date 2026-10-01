package com.fitcal.shared.auth

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
    fun testPostgrestDefaultSchemaIsFitCal() {
        SupabaseClientFactory.resetForTesting()
        val client = SupabaseClientFactory.getOrCreate()
        assertEquals("fitcal", client.postgrest.config.defaultSchema)
    }
}
