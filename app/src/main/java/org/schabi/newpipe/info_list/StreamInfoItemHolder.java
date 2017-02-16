package org.schabi.newpipe.info_list;

import android.icu.text.IDNA;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;

/**
 * Created by Christian Schabesberger on 01.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
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

public class StreamInfoItemHolder extends InfoItemHolder {

    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView,
            itemUploaderView,
            itemDurationView,
            itemUploadDateView,
            itemViewCountView;
    public final Button itemButton;

    public StreamInfoItemHolder(View v) {
        super(v);
        itemThumbnailView = (ImageView) v.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = (TextView) v.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = (TextView) v.findViewById(R.id.itemUploaderView);
        itemDurationView = (TextView) v.findViewById(R.id.itemDurationView);
        itemUploadDateView = (TextView) v.findViewById(R.id.itemUploadDateView);
        itemViewCountView = (TextView) v.findViewById(R.id.itemViewCountView);
        itemButton = (Button) v.findViewById(R.id.item_button);
    }

    @Override
    public InfoItem.InfoType infoType() {
        return InfoItem.InfoType.STREAM;
    }
}
