package org.schabi.newpipe.fragments.list

import org.schabi.newpipe.fragments.ViewContract

interface ListViewContract<I, N> : ViewContract<I> {
    fun showListFooter(show: Boolean)
    fun handleNextItems(result: N)
}
