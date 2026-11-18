package com.example.todolist

import android.content.Context
import android.util.Base64
import androidx.appcompat.app.AppCompatDelegate
import java.security.MessageDigest
import java.security.SecureRandom
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LocalPreferences {
    private const val PREFERENCES = "preferencias_todolist"
    private const val KEY_EMAIL = "correo_sesion"
    private const val KEY_NAME = "nombre_sesion"
    private const val KEY_DARK_MODE = "modo_oscuro"
    private const val KEY_SHOW_COMPLETED = "mostrar_completadas"

    fun saveSession(context: Context, user: UsuarioEntity) {
        preferences(context).edit()
            .putString(KEY_EMAIL, user.correo)
            .putString(KEY_NAME, user.nombre)
            .apply()
    }

    fun clearSession(context: Context) {
        preferences(context).edit()
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .apply()
    }

    fun email(context: Context): String? =
        preferences(context).getString(KEY_EMAIL, null)

    fun name(context: Context): String? =
        preferences(context).getString(KEY_NAME, null)

    fun isDarkMode(context: Context): Boolean =
        preferences(context).getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        applyTheme(context)
    }

    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode(context)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun showCompleted(context: Context): Boolean =
        preferences(context).getBoolean(KEY_SHOW_COMPLETED, true)

    fun setShowCompleted(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_SHOW_COMPLETED, enabled).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

object LocalPasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private val random = SecureRandom()

    fun create(password: CharArray): Pair<String, String> {
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = derive(password, salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP) to
            Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verify(password: CharArray, salt: String, expectedHash: String): Boolean {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val expectedBytes = Base64.decode(expectedHash, Base64.NO_WRAP)
        val actualBytes = derive(password, saltBytes)
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val passwordBuffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(passwordBuffer.remaining()).also(passwordBuffer::get)
        val mac = Mac.getInstance("HmacSHA256")
        val result = ByteArray(KEY_LENGTH_BITS / 8)
        try {
            mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
            val firstBlock = salt + byteArrayOf(0, 0, 0, 1)
            var current = mac.doFinal(firstBlock)
            current.copyInto(result)
            repeat(ITERATIONS - 1) {
                current = mac.doFinal(current)
                for (index in result.indices) {
                    result[index] = (result[index].toInt() xor current[index].toInt()).toByte()
                }
            }
            return result
        } finally {
            passwordBytes.fill(0)
            if (passwordBuffer.hasArray()) passwordBuffer.array().fill(0)
        }
    }
}
