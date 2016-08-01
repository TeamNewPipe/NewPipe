package org.schabi.newpipe;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.View;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.schabi.newpipe.ErrorActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;

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
    private Activity activity = null;
    private View rootView = null;

    public ImageErrorLoadingListener(Activity activity, View rootView, int serviceId) {
        this.activity = activity;
        this.serviceId= serviceId;
        this.rootView = rootView;
    }

    @Override
    public void onLoadingStarted(String imageUri, View view) {}

    @Override
    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
        ErrorActivity.reportError(activity,
                failReason.getCause(), null, rootView,
                ErrorActivity.ErrorInfo.make(ErrorActivity.LOAD_IMAGE,
                        ServiceList.getNameOfService(serviceId), imageUri,
                        R.string.could_not_load_image));
    }

    @Override
    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {

    }

    @Override
    public void onLoadingCancelled(String imageUri, View view) {}
}