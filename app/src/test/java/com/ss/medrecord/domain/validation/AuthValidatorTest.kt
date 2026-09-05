package com.ss.medrecord.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun `email accepts ordinary addresses`() {
        listOf(
            "a@b.co",
            "first.last@example.com",
            "user+tag@sub.domain.org",
        ).forEach { email ->
            assertTrue("expected $email to be valid", AuthValidator.validateEmail(email).isValid)
        }
    }

    @Test
    fun `email rejects malformed addresses`() {
        listOf(
            "",
            "   ",
            "no-at-sign.com",
            "two@@at.com",
            "trailing@dot.",
            "spaces in@example.com",
            "@example.com",
        ).forEach { email ->
            assertFalse("expected $email to be invalid", AuthValidator.validateEmail(email).isValid)
        }
    }

    @Test
    fun `email is trimmed before validation`() {
        assertTrue(AuthValidator.validateEmail("  user@example.com  ").isValid)
    }

    @Test
    fun `password requires length, a letter and a number`() {
        assertFalse(AuthValidator.validatePassword("short1").isValid)
        assertFalse(AuthValidator.validatePassword("alllettersnodigit").isValid)
        assertFalse(AuthValidator.validatePassword("12345678").isValid)
        assertTrue(AuthValidator.validatePassword("passw0rdd").isValid)
    }

    @Test
    fun `password floor is above the Firebase minimum of six`() {
        // Guards the deliberate decision to be stricter than the backend.
        assertTrue(AuthValidator.MIN_PASSWORD_LENGTH > 6)
        assertFalse(AuthValidator.validatePassword("abc123").isValid)
    }

    @Test
    fun `confirmation must match exactly`() {
        assertTrue(AuthValidator.validatePasswordConfirmation("passw0rdd", "passw0rdd").isValid)
        assertFalse(AuthValidator.validatePasswordConfirmation("passw0rdd", "passw0rdD").isValid)
        assertFalse(AuthValidator.validatePasswordConfirmation("passw0rdd", "").isValid)
    }

    @Test
    fun `name rejects blank and single characters`() {
        assertFalse(AuthValidator.validateName("").isValid)
        assertFalse(AuthValidator.validateName("   ").isValid)
        assertFalse(AuthValidator.validateName("A").isValid)
        assertTrue(AuthValidator.validateName("Jo").isValid)
    }

    @Test
    fun `invalid results carry a user-presentable message`() {
        val result = AuthValidator.validateEmail("nope")
        assertEquals("Enter a valid email address", result.errorOrNull)
    }
}
