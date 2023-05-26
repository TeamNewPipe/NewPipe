package org.schabi.newpipe.fragments

interface ViewContract<I> {
    fun showLoading()
    fun hideLoading()
    fun showEmptyState()
    fun handleResult(result: I)
    fun handleError()
}
