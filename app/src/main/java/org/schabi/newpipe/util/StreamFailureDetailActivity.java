package org.schabi.newpipe.util;

import android.app.NotificationManager;
import android.content.Context;
import org.schabi.newpipe.R;

import java.util.ArrayList;

public class StreamFailureDetailActivity extends android.app.Activity {
     @Override
     protected void onCreate(android.os.Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);
          ArrayList<String> failedDetails = getIntent().getStringArrayListExtra("failed_stream_details");
          int notificationId = getIntent().getIntExtra("notification_id_to_cancel", -1);
          if (notificationId != -1) {
              NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
              nm.cancel(notificationId);
          }
         new android.app.AlertDialog.Builder(this)
             .setTitle(getString(R.string.failed_streams))
             .setItems(failedDetails.toArray(new CharSequence[0]), null)
             .setPositiveButton(getString(R.string.ok), (dialog, which) -> finish())
             .setOnCancelListener(dialog -> finish())
             .show();
     }
 }