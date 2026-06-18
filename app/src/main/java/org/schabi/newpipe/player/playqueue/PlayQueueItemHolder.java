/*
 * SPDX-FileCopyrightText: 2016-2021 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.playqueue;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

public class PlayQueueItemHolder extends RecyclerView.ViewHolder {
    public final TextView itemVideoTitleView;
    public final TextView itemDurationView;
    final TextView itemAdditionalDetailsView;

    public final ImageView itemThumbnailView;
    final ImageView itemHandle;

    public final View itemRoot;

    PlayQueueItemHolder(final View v) {
        super(v);
        itemRoot = v.findViewById(R.id.itemRoot);
        itemVideoTitleView = v.findViewById(R.id.itemVideoTitleView);
        itemDurationView = v.findViewById(R.id.itemDurationView);
        itemAdditionalDetailsView = v.findViewById(R.id.itemAdditionalDetails);
        itemThumbnailView = v.findViewById(R.id.itemThumbnailView);
        itemHandle = v.findViewById(R.id.itemHandle);
    }
}
