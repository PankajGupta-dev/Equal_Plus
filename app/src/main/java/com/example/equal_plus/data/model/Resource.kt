package com.example.equal_plus.data.model

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>()
    data class Loading<out T>(val partialData: T? = null) : Resource<T>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Loading -> partialData
        is Error -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Loading -> partialData ?: default
        is Error -> default
    }

    inline fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Loading -> Loading(partialData?.let(transform))
        is Error -> Error(message, throwable)
    }

    inline fun onSuccess(action: (T) -> Unit): Resource<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (String, Throwable?) -> Unit): Resource<T> {
        if (this is Error) action(message, throwable)
        return this
    }
}

sealed interface RepositoryResult<out T> {
    data class Success<out T>(val data: T) : RepositoryResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : RepositoryResult<Nothing>
    data class FallbackCached<out T>(val cachedData: T, val error: String) : RepositoryResult<T>

    val isSuccess: Boolean get() = this is Success || this is FallbackCached

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is FallbackCached -> cachedData
        is Error -> null
    }
}
