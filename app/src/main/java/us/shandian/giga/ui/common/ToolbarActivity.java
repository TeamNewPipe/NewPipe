package us.shandian.giga.ui.common;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;

import org.schabi.newpipe.R;

public abstract class ToolbarActivity extends ActionBarActivity {
    protected Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResource());

        mToolbar = (Toolbar) this.findViewById(R.id.toolbar);

        setSupportActionBar(mToolbar);
    }

    protected abstract int getLayoutResource();
}
