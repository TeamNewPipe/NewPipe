package org.schabi.newpipe.views;

import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

public abstract class LongPressClickableSpan extends ClickableSpan {

    public abstract void onLongClick(@NonNull View view);

}
