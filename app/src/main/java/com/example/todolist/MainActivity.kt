package com.example.todolist

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.todolist.core.SessionManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var authRepository: AuthRepository
    private alteinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        LocalPreferences.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = sessionManager(this)
        lifecycleScope.launch {
            val isLoggedIn = sessionManager.isLoggedIn.first()
            if (isLoggedIn){
                val intent= Intent(this@MainActivity, activity_tareas::class.java)
                startActivity(intent)
                finish()
                return@launch
            }
        }

        val etCorreo = findViewById<TextInputEditText>(R.id.etCorreoLogin)
        val etPassword = findViewById<TextInputEditText>(R.id.etPasswordLogin)
        val btnIniciar = findViewById<Button>(R.id.btnIniciarSesion)
        val tvIrARegistro = findViewById<TextView>(R.id.tvIrARegistro)
        authRepository = AuthRepository(AppDatabase.obtenerBaseDatos(this))

        btnIniciar.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val password = etPassword.text.toString()
            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Ingresa tu correo y contraseña", Toast.LENGTH_SHORT).show()
            } else {
                btnIniciar.isEnabled = false
                authRepository.autenticar(correo, password.toCharArray()) { result ->
                    if (isFinishing || isDestroyed) return@autenticar
                    btnIniciar.isEnabled = true
                    result.onSuccess { user ->
                        if (user != null) {
                            LocalPreferences.saveSession(this, user)
                            startActivity(Intent(this, TareasActivity::class.java))
                        } else {
                            Toast.makeText(
                                this,
                                "Correo o contraseña incorrectos. Si aún no tienes cuenta, regístrate.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }.onFailure {
                        Toast.makeText(
                            this,
                            it.message ?: "No se pudo iniciar sesión.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        tvIrARegistro.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
    }

    override fun onDestroy() {
        if (::authRepository.isInitialized) authRepository.close()
        super.onDestroy()
    }
}
