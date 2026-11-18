package com.example.todolist

import android.database.sqlite.SQLiteConstraintException
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import java.util.Locale
import java.util.concurrent.Executors

class AuthRepository(private val database: AppDatabase) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun autenticar(
        correo: String,
        password: CharArray,
        callback: (Result<UsuarioEntity?>) -> Unit
    ) {
        executor.execute {
            val result = try {
                captureResult {
                    val email = normalizarCorreo(correo)
                    require(Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        "Ingresa un correo válido."
                    }
                    require(password.size <= 128) {
                        "La contraseña no puede superar 128 caracteres."
                    }
                    val user = database.usuarioDao().buscarPorCorreo(email)
                    if (user != null &&
                        LocalPasswordHasher.verify(password, user.contrasenaSalt, user.contrasenaHash)
                    ) user else null
                }
            } finally {
                password.fill('\u0000')
            }
            mainHandler.post { callback(result) }
        }
    }

    fun registrar(
        nombre: String,
        correo: String,
        password: CharArray,
        callback: (Result<UsuarioEntity>) -> Unit
    ) {
        executor.execute {
            val result = try {
                captureResult {
                    val name = nombre.trim()
                    val email = normalizarCorreo(correo)
                    require(name.isNotBlank()) { "Escribe tu nombre." }
                    require(name.length <= 80) { "El nombre no puede superar 80 caracteres." }
                    require(Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        "Ingresa un correo válido."
                    }
                    require(password.size >= 8) { "La contraseña debe tener al menos 8 caracteres." }
                    require(password.size <= 128) { "La contraseña no puede superar 128 caracteres." }

                    val (salt, hash) = LocalPasswordHasher.create(password)
                    val user = UsuarioEntity(
                        nombre = name,
                        correo = email,
                        contrasenaSalt = salt,
                        contrasenaHash = hash
                    )
                    val id = try {
                        database.usuarioDao().insertar(user)
                    } catch (_: SQLiteConstraintException) {
                        throw IllegalArgumentException("Ya existe una cuenta con ese correo.")
                    }
                    user.copy(id = id.toInt())
                }
            } finally {
                password.fill('\u0000')
            }
            mainHandler.post { callback(result) }
        }
    }

    fun close() {
        executor.shutdown()
    }

    private fun normalizarCorreo(correo: String) = correo.trim().lowercase(Locale.ROOT)
}
