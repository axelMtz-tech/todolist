package com.example.todolist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.Locale

class TareasViewModel(private val repository: TareaRepository) : ViewModel() {
    private val mutableTareas = MutableLiveData<List<TareaEntity>>(emptyList())
    val tareas: LiveData<List<TareaEntity>> = mutableTareas
    private val mutableError = MutableLiveData<String?>()
    val error: LiveData<String?> = mutableError

    private var correo = ""
    private var filtroEstado: EstadoTarea? = null
    private var busqueda = ""
    private var orden = OrdenTareas.RECIENTES
    private var mostrarCompletadas = true

    fun iniciar(correoUsuario: String) {
        correo = correoUsuario.trim().lowercase(Locale.ROOT)
        actualizarLista()
    }

    fun filtrarPor(estado: EstadoTarea?) {
        filtroEstado = estado
        actualizarLista()
    }

    fun buscar(texto: String) {
        busqueda = texto.trim()
        actualizarLista()
    }

    fun ordenarPor(tipo: OrdenTareas) {
        orden = tipo
        actualizarLista()
    }

    fun definirVisibilidadCompletadas(mostrar: Boolean) {
        if (mostrarCompletadas == mostrar) return
        mostrarCompletadas = mostrar
        actualizarLista()
    }

    fun crear(tarea: TareaEntity, callback: (Result<Unit>) -> Unit) {
        repository.insertar(tarea, callback)
    }

    fun editar(tarea: TareaEntity, callback: (Result<Unit>) -> Unit) {
        repository.actualizar(tarea, callback)
    }

    fun eliminar(id: Int, callback: (Result<Unit>) -> Unit) {
        repository.eliminar(id, correo, callback)
    }

    fun refrescar() {
        actualizarLista()
    }

    fun limpiarError() {
        mutableError.value = null
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    private fun actualizarLista() {
        if (correo.isBlank()) {
            mutableTareas.value = emptyList()
            return
        }
        repository.buscar(correo, mostrarCompletadas, filtroEstado, busqueda, orden) { result ->
            result.onSuccess { mutableTareas.value = it }
                .onFailure {
                    mutableTareas.value = emptyList()
                    mutableError.value = it.message ?: "No se pudieron cargar las tareas."
                }
        }
    }

    class Factory(private val repository: TareaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TareasViewModel::class.java))
            return TareasViewModel(repository) as T
        }
    }
}
