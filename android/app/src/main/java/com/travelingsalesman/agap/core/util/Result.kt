package com.travelingsalesman.agap.core.util

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = exception.localizedMessage) : Result<Nothing>() {
        constructor(message: String) : this(Exception(message), message)
    }
    object Loading : Result<Nothing>()
}
