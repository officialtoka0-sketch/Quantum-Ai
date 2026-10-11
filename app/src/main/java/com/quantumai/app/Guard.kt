package com.quantumai.app

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac

/** Защита от Lucky Patcher / подмены баланса. Поднимает планку, но не даёт 100% гарантии. */
object Guard {
    // >>> После подписи релизным ключом впиши SHA-256 сертификата (hex, нижний регистр). "" = проверка выключена <<<
    private const val EXPECTED_SIG_SHA256 = ""
    private val BAD_PACKAGES = listOf("com.dimonvideo.luckypatcher", "com.chelpus.lackypatch", "com.forpda.lp",
        "com.android.vending.billing.InAppBillingService.COIN", "com.android.vending.billing.InAppBillingService.LUCK",
        "com.gameguardian.gg", "catch_.me_.if_.you_.can_", "org.sbtools.gamehack")
    private val SU = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk")

    /** Подпись баланса ключом из Android Keystore: правка SharedPreferences ломает подпись. */
    fun mac(data: String): String {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias("qai_mac")) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            .apply { init(KeyGenParameterSpec.Builder("qai_mac", KeyProperties.PURPOSE_SIGN).build()) }.generateKey()
        val m = Mac.getInstance("HmacSHA256").apply { init(ks.getKey("qai_mac", null)) }
        return m.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    fun compromised(c: Context): Boolean {
        val pm = c.packageManager
        if (BAD_PACKAGES.any { try { pm.getPackageInfo(it, 0); true } catch (e: Exception) { false } }) return true
        if (SU.any { File(it).exists() }) return true
        if (EXPECTED_SIG_SHA256.isNotEmpty()) {
            val sig = pm.getPackageInfo(c.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull() ?: return true
            val h = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            if (h != EXPECTED_SIG_SHA256) return true
        }
        return false
    }
}
