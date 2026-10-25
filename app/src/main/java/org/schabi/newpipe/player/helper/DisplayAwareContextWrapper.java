package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.Display;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Context for the player views, which are inflated with the player service context. That context
 * is bound to the default display, so windows added on behalf of a view (e.g. popup menus and
 * hover tooltips) would be added to the wrong display when the player is shown on a secondary
 * display, like Samsung DeX, causing a {@link android.view.WindowManager.BadTokenException}. This
 * wrapper returns the {@link android.view.WindowManager} of the display the anchor view is on.
 */
public class DisplayAwareContextWrapper extends ContextWrapper {
    @Nullable
    private View anchor;

    public DisplayAwareContextWrapper(@NonNull final Context base) {
        super(base);
    }

    public void setAnchor(@Nullable final View anchor) {
        this.anchor = anchor;
    }

    @Override
    public Object getSystemService(@NonNull final String name) {
        if (Context.WINDOW_SERVICE.equals(name) && anchor != null) {
            final Display display = anchor.getDisplay();
            if (display != null) {
                return getBaseContext().createDisplayContext(display).getSystemService(name);
            }
        }
        return super.getSystemService(name);
    }
}
