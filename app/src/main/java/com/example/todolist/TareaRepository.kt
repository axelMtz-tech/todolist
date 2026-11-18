package com.example.todolist

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TareaRepository(private val database: AppDatabase) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun buscar(
        correo: String,
        mostrarCompletadas: Boolean,
        estado: EstadoTarea?,
        query: String,
        orden: OrdenTareas,
        callback: (Result<List<TareaEntity>>) -> Unit
    ) {
        executor.execute {
            val result = captureResult {
                database.tareaDao().buscarTareas(
                    correo,
                    mostrarCompletadas,
                    estado?.name,
                    query.trim(),
                    orden.name
                )
            }
            mainHandler.post { callback(result) }
        }
    }

    fun insertar(tarea: TareaEntity, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = captureResult {
                TareaValidator.validar(tarea)
                database.tareaDao().insertarTarea(tarea)
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun actualizar(tarea: TareaEntity, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = captureResult {
                TareaValidator.validar(tarea)
                check(database.tareaDao().actualizarTarea(
                    id = tarea.id,
                    correo = requireNotNull(tarea.propietarioEmail),
                    titulo = tarea.titulo.trim(),
                    fechaHora = tarea.fechaHora,
                    completada = tarea.estado == EstadoTarea.COMPLETADA.name,
                    estado = tarea.estado,
                    prioridad = tarea.prioridad,
                    categoria = tarea.categoria.trim()
                ) == 1) { "La tarea ya no existe o no pertenece a esta cuenta." }
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun eliminar(id: Int, correo: String, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = captureResult {
                check(database.tareaDao().eliminarTarea(id, correo) == 1) {
                    "La tarea ya no existe o no pertenece a esta cuenta."
                }
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun close() {
        executor.shutdown()
    }

}
