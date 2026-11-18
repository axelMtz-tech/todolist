package com.example.todolist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TareaValidatorTest {
    @Test
    fun acceptsValidTaskWithEachWorkflowState() {
        EstadoTarea.entries.forEach { state ->
            TareaValidator.validar(task(estado = state.name))
        }
    }

    @Test
    fun rejectsBlankOrOversizedTitle() {
        assertEquals(
            "Escribe el nombre de la tarea.",
            assertThrows(IllegalArgumentException::class.java) {
                TareaValidator.validar(task(titulo = "   "))
            }.message
        )
        assertThrows(IllegalArgumentException::class.java) {
            TareaValidator.validar(task(titulo = "x".repeat(121)))
        }
    }

    @Test
    fun rejectsUnsupportedPriorityAndOwnerlessTask() {
        assertThrows(IllegalArgumentException::class.java) {
            TareaValidator.validar(task(prioridad = "Urgente"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TareaValidator.validar(task(propietarioEmail = null))
        }
    }

    private fun task(
        titulo: String = "Preparar entrega",
        estado: String = EstadoTarea.PENDIENTE.name,
        prioridad: String = "Media",
        propietarioEmail: String? = "ana@example.com"
    ) = TareaEntity(
        titulo = titulo,
        fechaHora = "2026-11-09",
        completada = estado == EstadoTarea.COMPLETADA.name,
        estado = estado,
        prioridad = prioridad,
        categoria = "Escuela",
        propietarioEmail = propietarioEmail
    )
}
