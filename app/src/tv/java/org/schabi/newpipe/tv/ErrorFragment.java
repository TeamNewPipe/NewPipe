package org.schabi.newpipe.tv;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import org.schabi.newpipe.R;

public class ErrorFragment extends android.support.v17.leanback.app.ErrorFragment {
    private static final String TAG = "ErrorFragment";
    private static final boolean TRANSLUCENT = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.app_name));
    }

    void setErrorContent() {
        setImageDrawable(getResources().getDrawable(R.drawable.lb_ic_sad_cloud));
        setMessage(getString(R.string.error_fragment_message));
        setDefaultBackground(TRANSLUCENT);

        setButtonText(getString(R.string.dismiss_error));
        setButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                getFragmentManager().beginTransaction().remove(ErrorFragment.this).commit();
            }
        });
    }
}
