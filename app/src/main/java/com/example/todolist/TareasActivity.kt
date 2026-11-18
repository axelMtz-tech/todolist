package com.example.todolist

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class TareasActivity : AppCompatActivity() {
    private lateinit var correoUsuario: String
    private lateinit var nombreUsuario: String
    private lateinit var viewModel: TareasViewModel
    private lateinit var contenedorTareas: LinearLayout
    private lateinit var tvProgreso: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvVacio: TextView
    private var filtroActual: EstadoTarea? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        LocalPreferences.applyTheme(this)
        super.onCreate(savedInstanceState)

        correoUsuario = LocalPreferences.email(this)
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        if (correoUsuario.isBlank()) {
            volverAlLogin()
            return
        }

        setContentView(R.layout.activity_tareas)
        nombreUsuario = LocalPreferences.name(this)
            ?.takeIf { it.isNotBlank() }
            ?: correoUsuario.substringBefore("@").ifBlank { "Usuario" }
        val database = AppDatabase.obtenerBaseDatos(this)
        viewModel = ViewModelProvider(
            this,
            TareasViewModel.Factory(TareaRepository(database))
        )[TareasViewModel::class.java]

        contenedorTareas = findViewById(R.id.contenedorTareas)
        tvProgreso = findViewById(R.id.tvProgresoTareas)
        progressBar = findViewById(R.id.progressBarTareas)
        tvVacio = findViewById(R.id.tvListaVacia)

        findViewById<TextView>(R.id.tvSaludoUsuario).text = "Hola, $nombreUsuario 👋"
        findViewById<TextView>(R.id.tvFechaActual).text =
            DateFormat.getDateInstance(DateFormat.FULL).format(Date())
        findViewById<ImageView>(R.id.btnIrAConfiguracion).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }
        findViewById<FloatingActionButton>(R.id.fabCrearTarea).setOnClickListener {
            mostrarDialogoTarea()
        }

        configurarFiltros()
        configurarBusquedaYOrden()
        viewModel.tareas.observe(this) { renderizarTareas(it) }
        viewModel.error.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                viewModel.limpiarError()
            }
        }
        viewModel.definirVisibilidadCompletadas(LocalPreferences.showCompleted(this))
        viewModel.iniciar(correoUsuario)
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.definirVisibilidadCompletadas(LocalPreferences.showCompleted(this))
            viewModel.refrescar()
        }
    }

    private fun volverAlLogin() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun configurarFiltros() {
        val opciones = listOf(
            Triple(R.id.btnFiltroTodas, null, "Todas"),
            Triple(R.id.btnFiltroPendientes, EstadoTarea.PENDIENTE, "Pendientes"),
            Triple(R.id.btnFiltroEnProgreso, EstadoTarea.EN_PROGRESO, "En progreso"),
            Triple(R.id.btnFiltroCompletadas, EstadoTarea.COMPLETADA, "Completadas")
        )
        opciones.forEach { (id, estado, _) ->
            findViewById<MaterialButton>(id).setOnClickListener {
                filtroActual = estado
                opciones.forEach { (buttonId, buttonState, _) ->
                    val button = findViewById<MaterialButton>(buttonId)
                    val selected = buttonState == filtroActual
                    button.backgroundTintList = ColorStateList.valueOf(
                        Color.parseColor(if (selected) "#4F46E5" else "#E2E8F0")
                    )
                    button.setTextColor(Color.parseColor(if (selected) "#FFFFFF" else "#334155"))
                }
                viewModel.filtrarPor(filtroActual)
            }
        }
    }

    private fun configurarBusquedaYOrden() {
        val search = findViewById<TextInputEditText>(R.id.etBuscarTareas)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.buscar(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        val options = listOf("Más recientes", "Prioridad", "Título")
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        findViewById<android.widget.Spinner>(R.id.spOrdenTareas).apply {
            this.adapter = adapter
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.ordenarPor(
                        when (position) {
                            1 -> OrdenTareas.PRIORIDAD
                            2 -> OrdenTareas.TITULO
                            else -> OrdenTareas.RECIENTES
                        }
                    )
                }
            }
        }
    }

    private fun renderizarTareas(tareas: List<TareaEntity>) {
        contenedorTareas.removeAllViews()
        tvVacio.visibility = if (tareas.isEmpty()) View.VISIBLE else View.GONE
        tvVacio.text = if (filtroActual == null) {
            "No se encontraron tareas. Pulsa + para crear una."
        } else {
            "No hay tareas en este estado."
        }
        tareas.forEach { tarea -> contenedorTareas.addView(crearTarjetaTarea(tarea)) }

        val completadas = tareas.count { it.estado == EstadoTarea.COMPLETADA.name }
        tvProgreso.text = "Progreso: $completadas/${tareas.size} tareas visibles"
        progressBar.max = tareas.size.coerceAtLeast(1)
        progressBar.progress = completadas
    }

    private fun crearTarjetaTarea(tarea: TareaEntity): View {
        val card = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(getColor(R.color.card_background))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            isClickable = true
            isFocusable = true
            setOnClickListener { mostrarDialogoTarea(tarea) }
            setOnLongClickListener {
                confirmarEliminacion(tarea)
                true
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12))
        }
        val status = MaterialButton(this).apply {
            text = etiquetaEstado(tarea.estado)
            textSize = 10f
            minWidth = 0
            setPadding(dp(8), dp(2), dp(8), dp(2))
            backgroundTintList = ColorStateList.valueOf(colorEstado(tarea.estado))
            setTextColor(Color.WHITE)
            contentDescription = "Cambiar estado de ${tarea.titulo}"
            setOnClickListener { seleccionarEstado(tarea) }
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = tarea.titulo
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val details = TextView(this).apply {

            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            gravity = Gravity.CENTER_VERTICAL
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, 0, 0)
        }
        val detailsParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        actions.addView(details, detailsParams)
        actions.addView(taskAction("Edit", textColor = Color.WHITE) {
            mostrarDialogoTarea(tarea)
        })
        actions.addView(taskAction("Eliminar", textColor = Color.WHITE) { confirmarEliminacion(tarea) })
        textColumn.addView(title)
        row.addView(status)
        row.addView(textColumn)
        card.addView(row)
        card.addView(actions)
        return card
    }

    private fun taskAction(
        text: String,
        textColor: Int = Color.rgb(79, 70, 229),
        onClick: () -> Unit
    ) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 10f
        minWidth = 0
        minHeight = dp(32)
        insetTop = 0
        insetBottom = 0
        setPadding(dp(5), 0, dp(5), 0)
        setTextColor(textColor)
        setOnClickListener { onClick() }
    }

    private fun seleccionarEstado(tarea: TareaEntity) {
        val estados = EstadoTarea.entries
        AlertDialog.Builder(this)
            .setTitle("Estado de la tarea")
            .setItems(estados.map { etiquetaEstado(it.name) }.toTypedArray()) { _, position ->
                guardarTarea(tarea.copy(
                    estado = estados[position].name,
                    completada = estados[position] == EstadoTarea.COMPLETADA
                ))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoTarea(tarea: TareaEntity? = null) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_crear_tarea, null)
        dialog.setContentView(view)
        view.findViewById<TextView>(R.id.tvTituloDialogoTarea).text =
            if (tarea == null) "Crear Nueva Tarea" else "Editar Tarea"
        val titleField = view.findViewById<TextInputEditText>(R.id.etTituloTarea)
        titleField.setText(tarea?.titulo.orEmpty())

        var priority = tarea?.prioridad ?: "Alta"
        var category = tarea?.categoria ?: "Escuela"
        var status = tarea?.estado?.let { value ->
            EstadoTarea.entries.firstOrNull { it.name == value }
        } ?: EstadoTarea.PENDIENTE

        val priorities = listOf(
            view.findViewById<MaterialButton>(R.id.btnPrioridadAlta) to "Alta",
            view.findViewById<MaterialButton>(R.id.btnPrioridadMedia) to "Media",
            view.findViewById<MaterialButton>(R.id.btnPrioridadBaja) to "Baja"
        )
        val categories = listOf(
            view.findViewById<MaterialButton>(R.id.btnCatEscuela) to "Escuela",
            view.findViewById<MaterialButton>(R.id.btnCatTrabajo) to "Trabajo",
            view.findViewById<MaterialButton>(R.id.btnCatPersonal) to "Personal"
        )
        val statuses = listOf(
            view.findViewById<MaterialButton>(R.id.btnEstadoPendiente) to EstadoTarea.PENDIENTE,
            view.findViewById<MaterialButton>(R.id.btnEstadoProgreso) to EstadoTarea.EN_PROGRESO,
            view.findViewById<MaterialButton>(R.id.btnEstadoCompletada) to EstadoTarea.COMPLETADA
        )
        fun <T> markSelection(buttons: List<Pair<MaterialButton, T>>, selected: T) {
            buttons.forEach { (button, value) -> button.alpha = if (value == selected) 1f else 0.55f }
        }
        fun renderSelections() {
            markSelection(priorities, priority)
            markSelection(categories, category)
            markSelection(statuses, status)
        }
        priorities.forEach { (button, value) ->
            button.setOnClickListener { priority = value; renderSelections() }
        }
        categories.forEach { (button, value) ->
            button.setOnClickListener { category = value; renderSelections() }
        }
        statuses.forEach { (button, value) ->
            button.setOnClickListener { status = value; renderSelections() }
        }
        renderSelections()

        view.findViewById<MaterialButton>(R.id.btnGuardarTareaDB).apply {
            text = if (tarea == null) "GUARDAR TAREA" else "GUARDAR CAMBIOS"
            setOnClickListener {
                val title = titleField.text?.toString()?.trim().orEmpty()
                if (title.isBlank()) {
                    titleField.error = "Escribe el nombre de la tarea"
                    return@setOnClickListener
                }
                if (title.length > 120) {
                    titleField.error = "El título no puede superar 120 caracteres"
                    return@setOnClickListener
                }
                val savedTask = TareaEntity(
                    id = tarea?.id ?: 0,
                    titulo = title,
                    fechaHora = tarea?.fechaHora ?: DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT
                    ).format(Date()),
                    completada = status == EstadoTarea.COMPLETADA,
                    estado = status.name,
                    prioridad = priority,
                    categoria = category,
                    propietarioEmail = correoUsuario,
                    createdAt = tarea?.createdAt ?: System.currentTimeMillis()
                )
                if (tarea == null) {
                    viewModel.crear(savedTask) { result ->
                        completarGuardado(result, dialog, "Tarea creada")
                    }
                } else {
                    viewModel.editar(savedTask) { result ->
                        completarGuardado(result, dialog, "Tarea actualizada")
                    }
                }
                isEnabled = false
            }
        }
        dialog.show()
    }

    private fun guardarTarea(tarea: TareaEntity) {
        viewModel.editar(tarea) { result ->
            result.onSuccess { viewModel.refrescar() }
                .onFailure { mostrarError(it) }
        }
    }

    private fun completarGuardado(
        result: Result<Unit>,
        dialog: BottomSheetDialog,
        successMessage: String
    ) {
        result.onSuccess {
            dialog.dismiss()
            viewModel.refrescar()
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
        }.onFailure {
            dialog.findViewById<MaterialButton>(R.id.btnGuardarTareaDB)?.isEnabled = true
            mostrarError(it)
        }
    }

    private fun confirmarEliminacion(tarea: TareaEntity) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar tarea")
            .setMessage("¿Quieres eliminar “${tarea.titulo}”?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.eliminar(tarea.id) { result ->
                    result.onSuccess {
                        viewModel.refrescar()
                        Toast.makeText(this, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                    }.onFailure { mostrarError(it) }
                }
            }
            .show()
    }

    private fun mostrarError(error: Throwable) {
        Toast.makeText(
            this,
            error.message ?: "No se pudo completar la operación.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun etiquetaEstado(estado: String): String = when (estado) {
        EstadoTarea.EN_PROGRESO.name -> "En progreso"
        EstadoTarea.COMPLETADA.name -> "Completada"
        else -> "Pendiente"
    }

    private fun colorEstado(estado: String): Int = when (estado) {
        EstadoTarea.EN_PROGRESO.name -> Color.rgb(245, 158, 11)
        EstadoTarea.COMPLETADA.name -> Color.rgb(34, 139, 94)
        else -> Color.rgb(79, 70, 229)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
