package org.schabi.newpipe.util;

import android.os.Bundle;
import org.schabi.newpipe.R;

public class FilePickerActivityHelper extends com.nononsenseapps.filepicker.FilePickerActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        String currentTheme = ThemeHelper.getSelectedTheme(this);
        if(currentTheme.equals("light_theme")) {
            this.setTheme(R.style.FilePickerThemeLight);
        } else {
            this.setTheme(R.style.FilePickerThemeDark);
        }
        super.onCreate(savedInstanceState);
    }
}
