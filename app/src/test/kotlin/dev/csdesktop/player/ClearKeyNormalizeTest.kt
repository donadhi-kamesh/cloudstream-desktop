package dev.csdesktop.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClearKeyNormalizeTest {
    @Test
    fun `normalizes hex kid`() {
        val hex = ClearKeyProxy.normalizeHex("01010101010101010101010101010101")
        assertEquals("01010101010101010101010101010101", hex)
    }

    @Test
    fun `emits jwks json`() {
        val json = ClearKeyProxy.clearkeyJson(ClearKeyProxy.KeySet("aa", "bb"))
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"kty\":\"oct\""))
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("keys"))
    }
}
