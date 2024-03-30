package org.schabi.newpipe.local.subscription.services

open interface ImportExportEventListener {
    /**
     * Called when the size has been resolved.
     *
     * @param size how many items there are to import/export
     */
    fun onSizeReceived(size: Int)

    /**
     * Called every time an item has been parsed/resolved.
     *
     * @param itemName the name of the subscription item
     */
    fun onItemCompleted(itemName: String)
}
