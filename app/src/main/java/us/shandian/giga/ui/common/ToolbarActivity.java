package us.shandian.giga.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.marcinorlowski.fonty.Fonty;

import org.schabi.newpipe.R;

public abstract class ToolbarActivity extends AppCompatActivity {
    protected Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final String preferredFont = getPreferredFont(this);
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResource());

        mToolbar = this.findViewById(R.id.toolbar);

        setSupportActionBar(mToolbar);

        if (!preferredFont.equals(getString(R.string.default_font_key))) {
            Fonty.setFonts(this);
        }
    }
    public String getPreferredFont(final Context context) {
        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        return preferences.getString("preferred_font", (getString(R.string.default_font_key)));
    }


    protected abstract int getLayoutResource();
}
