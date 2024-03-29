package org.schabi.newpipe.info_list;

/**
 * Item view mode for streams & playlist listing screens.
 */
public enum ItemViewMode {
    /**
     * Default mode.
     */
    AUTO,
    /**
     * Full width list item with thumb on the left and two line title & uploader in right.
     */
    LIST,
    /**
     * Grid mode places two cards per row.
     */
    GRID,
    /**
     * A full width card in phone - portrait.
     */
    CARD
}
