package com.example.todolist

internal inline fun <T> captureResult(operation: () -> T): Result<T> =
    try {
        Result.success(operation())
    } catch (error: Exception) {
        Result.failure(error)
    }
