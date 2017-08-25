package org.schabi.newpipe.info_list;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;

/**
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ChannelInfoItemHolder .java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class PlaylistInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemPlaylistTitleView;
    public final TextView itemAdditionalDetailView;

    public final View itemRoot;

    PlaylistInfoItemHolder(View v) {
        super(v);
        itemRoot = v.findViewById(R.id.itemRoot);
        itemThumbnailView = v.findViewById(R.id.itemThumbnailView);
        itemPlaylistTitleView = v.findViewById(R.id.itemPlaylistTitleView);
        itemAdditionalDetailView = v.findViewById(R.id.itemAdditionalDetails);
    }

    @Override
    public InfoItem.InfoType infoType() {
        return InfoItem.InfoType.PLAYLIST;
    }
}
