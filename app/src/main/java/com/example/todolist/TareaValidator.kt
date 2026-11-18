package com.example.todolist

object TareaValidator {
    private val validStates = EstadoTarea.entries.mapTo(mutableSetOf()) { it.name }
    private val validPriorities = setOf("Alta", "Media", "Baja")

    fun validar(tarea: TareaEntity) {
        require(tarea.titulo.isNotBlank()) { "Escribe el nombre de la tarea." }
        require(tarea.titulo.trim().length <= 120) {
            "El título no puede superar 120 caracteres."
        }
        require(tarea.estado in validStates) { "El estado seleccionado no es válido." }
        require(tarea.prioridad in validPriorities) { "La prioridad seleccionada no es válida." }
        require(tarea.categoria.isNotBlank() && tarea.categoria.trim().length <= 40) {
            "La categoría debe tener entre 1 y 40 caracteres."
        }
        require(!tarea.propietarioEmail.isNullOrBlank()) {
            "No se encontró la cuenta propietaria de la tarea."
        }
    }
}
