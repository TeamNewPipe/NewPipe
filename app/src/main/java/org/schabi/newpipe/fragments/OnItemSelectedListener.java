package org.schabi.newpipe.fragments;

import org.schabi.newpipe.extractor.StreamingService;

/**
 * Interface for communication purposes between activity and fragment
 */
public interface OnItemSelectedListener {
    void onItemSelected(StreamingService.LinkType linkType, int serviceId, String url, String name);
}
