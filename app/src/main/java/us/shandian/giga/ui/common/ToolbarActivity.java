package us.shandian.giga.ui.common;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.schabi.newpipe.R;

public abstract class ToolbarActivity extends AppCompatActivity {
    protected Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResource());

        mToolbar = this.findViewById(R.id.toolbar);

        setSupportActionBar(mToolbar);
    }

    protected abstract int getLayoutResource();
}
