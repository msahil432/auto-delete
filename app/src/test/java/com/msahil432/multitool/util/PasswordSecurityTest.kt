package com.msahil432.multitool.util

import org.junit.Assert.*
import org.junit.Test

class PasswordSecurityTest {

    @Test
    fun testHashAndVerifySuccess() {
        val password = "mySecretPassword123"
        val hash = PasswordSecurity.hashPassword(password)

        assertNotNull(hash)
        assertTrue(hash.contains(":"))

        // Correct password verifies successfully
        assertTrue(PasswordSecurity.verifyPassword(password, hash))
    }

    @Test
    fun testWrongPasswordFails() {
        val password = "correctPassword"
        val hash = PasswordSecurity.hashPassword(password)

        assertFalse(PasswordSecurity.verifyPassword("wrongPassword", hash))
        assertFalse(PasswordSecurity.verifyPassword("", hash))
        assertFalse(PasswordSecurity.verifyPassword("CORRECTPASSWORD", hash))
    }

    @Test
    fun testSaltUniqueness() {
        val password = "samePassword"
        val hash1 = PasswordSecurity.hashPassword(password)
        val hash2 = PasswordSecurity.hashPassword(password)

        // Random salts mean hashes will differ
        assertNotEquals(hash1, hash2)

        // But both verify against their respective hashes
        assertTrue(PasswordSecurity.verifyPassword(password, hash1))
        assertTrue(PasswordSecurity.verifyPassword(password, hash2))
    }

    @Test
    fun testMalformedHashHandling() {
        assertFalse(PasswordSecurity.verifyPassword("password", ""))
        assertFalse(PasswordSecurity.verifyPassword("password", "invalid_format_without_colon"))
        assertFalse(PasswordSecurity.verifyPassword("password", "nothex:nothex"))
    }
}
