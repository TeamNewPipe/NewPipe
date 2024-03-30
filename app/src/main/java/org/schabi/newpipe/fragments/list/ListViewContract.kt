package org.schabi.newpipe.fragments.list

import org.schabi.newpipe.fragments.ViewContract

open interface ListViewContract<I, N> : ViewContract<I> {
    fun showListFooter(show: Boolean)
    fun handleNextItems(result: N)
}
