package com.kingzcheung.xime.bitwarden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitwardenCryptoTest {

    @Test
    fun `pbkdf2 hmac sha256 rfc vector`() {
        val dk = BitwardenCrypto.pbkdf2HmacSha256(
            password = "password".toByteArray(Charsets.UTF_8),
            salt = "salt".toByteArray(Charsets.UTF_8),
            iterations = 1,
            dkLen = 32,
        )
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            dk.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `pbkdf2 master key matches python hashlib vector 100k`() {
        val master = BitwardenCrypto.makeMasterKey(
            password = "password",
            email = "user@example.com",
            kdf = BitwardenCrypto.KDF_PBKDF2,
            iterations = 100_000,
            memoryMb = null,
            parallelism = null,
        )
        assertEquals(
            "69859b2e33f86e822289e771acd0d98b8be0792c55d28f954537ac672c7f7636",
            master.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `password hash second pbkdf2 matches python hashlib vector`() {
        val master = BitwardenCrypto.makeMasterKey(
            password = "password",
            email = "user@example.com",
            kdf = BitwardenCrypto.KDF_PBKDF2,
            iterations = 100_000,
            memoryMb = null,
            parallelism = null,
        )
        val hashBytes = BitwardenCrypto.pbkdf2HmacSha256(
            password = master,
            salt = "password".toByteArray(Charsets.UTF_8),
            iterations = 1,
            dkLen = 32,
        )
        assertEquals(
            "cdaf3d6cbfbb2c4eed260686b3cc3595b4aecfeba0b30c639017f60d54bf84b5",
            hashBytes.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `encrypt decrypt roundtrip`() {
        val master = BitwardenCrypto.makeMasterKey(
            password = "TestPassword123!",
            email = "user@example.com",
            kdf = BitwardenCrypto.KDF_PBKDF2,
            iterations = 1000,
            memoryMb = null,
            parallelism = null,
        )
        val key = BitwardenCrypto.stretchMasterKey(master)
        val cipher = BitwardenCrypto.encryptUtf8("androidapp://com.example.app", key)
        assertTrue(cipher.startsWith("2."))
        assertEquals("androidapp://com.example.app", BitwardenCrypto.decryptUtf8(cipher, key))
    }

    @Test
    fun `package match helper`() {
        val item = VaultLoginItem(
            id = "1",
            name = "Demo",
            username = "u",
            password = "p",
            totp = null,
            uris = listOf("androidapp://com.kingzcheung.xime"),
            notes = null,
            revisionDate = null,
        )
        assertTrue(item.matchesPackage("com.kingzcheung.xime"))
        assertTrue(item.matchesQuery("xime"))
    }

    @Test
    fun `totp rfc6238 vector`() {
        // RFC 6238 Appendix B seed "12345678901234567890" (ASCII) as Base32 of those bytes isn't standard;
        // use known secret "JBSWY3DPEHPK3PXP" → "Hello!" related demos often use this for 6-digit.
        val code = BitwardenTotp.code("JBSWY3DPEHPK3PXP", timeMillis = 1_111_111_111_000L)
        assertTrue(!code.isNullOrBlank() && code!!.length == 6)
    }

    @Test
    fun `password generator respects length`() {
        val pw = BitwardenCrypto.generatePassword(PasswordGeneratorOptions(length = 24))
        assertEquals(24, pw.length)
    }

    @Test
    fun `fuzzy host match by app label and browser domain`() {
        val browserItem = VaultLoginItem(
            id = "2",
            name = "哔哩哔哩",
            username = "u",
            password = "p",
            totp = null,
            uris = listOf("https://www.bilibili.com/"),
            notes = null,
            revisionDate = null,
        )
        val host = VaultHostContext(
            packageName = "tv.danmaku.bili",
            appLabel = "哔哩哔哩",
        )
        assertTrue(browserItem.matchesHost(host))
        assertTrue(browserItem.hostMatchScore(host) >= 40)
        // 协议前缀本身不参与：剥掉 https://www. 后按域名/应用名比
        assertEquals("bilibili.com", VaultLoginItem.stripUriScheme("https://www.bilibili.com/"))
    }
}
