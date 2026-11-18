package com.example.todolist

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class ConfiguracionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        LocalPreferences.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)

        findViewById<TextView>(R.id.tvNombreCuenta).text =
            LocalPreferences.name(this) ?: "Mi Cuenta"
        findViewById<TextView>(R.id.tvCorreoCuenta).text =
            LocalPreferences.email(this).orEmpty()



        val switchCompleted = findViewById<SwitchCompat>(R.id.switchMostrarCompletadas)
        switchCompleted.isChecked = LocalPreferences.showCompleted(this)
        switchCompleted.setOnCheckedChangeListener { _, checked ->
            LocalPreferences.setShowCompleted(this, checked)
        }

        val btnCerrarSesion = findViewById<Button>(R.id.btnCerrarSesion)

        btnCerrarSesion.setOnClickListener {
            LocalPreferences.clearSession(this)
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}