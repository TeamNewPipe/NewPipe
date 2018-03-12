package org.schabi.newpipe.subscription;

public interface ImportExportEventListener {
    /**
     * Called when the size has been resolved.
     *
     * @param size how many items there are to import/export
     */
    void onSizeReceived(int size);

    /**
     * Called everytime an item has been parsed/resolved.
     *
     * @param itemName the name of the subscription item
     */
    void onItemCompleted(String itemName);
}