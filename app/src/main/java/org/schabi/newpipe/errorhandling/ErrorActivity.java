package org.schabi.newpipe.errorhandling;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.schabi.newpipe.R;

public class ErrorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error);
    }
}
