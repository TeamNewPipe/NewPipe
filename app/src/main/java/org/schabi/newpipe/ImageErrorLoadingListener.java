package org.schabi.newpipe;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;

/**
 * Created by Christian Schabesberger on 01.08.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * StreamInfoItemViewCreator.java is part of NewPipe.
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

public class ImageErrorLoadingListener implements ImageLoadingListener {

    private int serviceId = -1;
    private Context context = null;
    private View rootView = null;

    public ImageErrorLoadingListener(Context context, View rootView, int serviceId) {
        this.context = context;
        this.serviceId= serviceId;
        this.rootView = rootView;
    }

    @Override
    public void onLoadingStarted(String imageUri, View view) {}

    @Override
    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
        ErrorActivity.reportError(context,
                failReason.getCause(), null, rootView,
                ErrorActivity.ErrorInfo.make(UserAction.LOAD_IMAGE,
                        NewPipe.getNameOfService(serviceId), imageUri,
                        R.string.could_not_load_image));
    }

    @Override
    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {

    }

    @Override
    public void onLoadingCancelled(String imageUri, View view) {}
}