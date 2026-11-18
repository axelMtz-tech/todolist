package com.example.todolist

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class RegistroActivity : AppCompatActivity() {
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        LocalPreferences.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        val nameField = findViewById<TextInputEditText>(R.id.etNombreRegistro)
        val emailField = findViewById<TextInputEditText>(R.id.etCorreoRegistro)
        val passwordField = findViewById<TextInputEditText>(R.id.etPasswordRegistro)
        val registerButton = findViewById<Button>(R.id.btnCompletarRegistro)
        val backButton = findViewById<TextView>(R.id.tvVolverLogin)
        authRepository = AuthRepository(AppDatabase.obtenerBaseDatos(this))

        registerButton.setOnClickListener {
            val name = nameField.text?.toString().orEmpty()
            val email = emailField.text?.toString().orEmpty()
            val password = passwordField.text?.toString().orEmpty()
            if (name.isBlank() || email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerButton.isEnabled = false
            authRepository.registrar(name, email, password.toCharArray()) { result ->
                if (isFinishing || isDestroyed) return@registrar
                registerButton.isEnabled = true
                result.onSuccess { user ->
                    LocalPreferences.saveSession(this, user)
                    Toast.makeText(this, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, TareasActivity::class.java))
                    finish()
                }.onFailure {
                    Toast.makeText(
                        this,
                        it.message ?: "No se pudo crear la cuenta.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        backButton.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        if (::authRepository.isInitialized) authRepository.close()
        super.onDestroy()
    }
}
