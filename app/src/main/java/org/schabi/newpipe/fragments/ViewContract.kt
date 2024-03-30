package org.schabi.newpipe.fragments

open interface ViewContract<I> {
    fun showLoading()
    fun hideLoading()
    fun showEmptyState()
    fun handleResult(result: I)
    fun handleError()
}
