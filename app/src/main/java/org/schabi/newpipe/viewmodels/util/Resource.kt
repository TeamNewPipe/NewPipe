package org.schabi.newpipe.viewmodels.util

sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    class Success<T>(val data: T) : Resource<T>()
    class Error(val throwable: Throwable) : Resource<Nothing>()
}
