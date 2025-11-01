package org.schabi.newpipe.player.ui;

// ... (imports as in current file)

public abstract class VideoPlayerUi extends PlayerUi implements SeekBar.OnSeekBarChangeListener,
        PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {
    // ... (existing fields and setup code)

    private float lastNonHoldSpeed = 1.0f;
    private boolean isHolding = false;

    // Call this after binding and player are ready
    private void setupHoldNXGesture() {
        final GestureDetector.SimpleOnGestureListener gestureListener =
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(final MotionEvent e) {
                if (!isHolding) {
                    lastNonHoldSpeed = player.getPlaybackSpeed();
                    // No hardcoded default: use the value already set in Tempo dialog
                    player.setPlaybackSpeed(player.getPlaybackSpeed());
                    isHolding = true;
                }
            }
        };
        final GestureDetector gestureDetector = new GestureDetector(context, gestureListener);
        binding.getRoot().setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if ((event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) && isHolding) {
                player.setPlaybackSpeed(lastNonHoldSpeed);
                isHolding = false;
            }
            return false;
        });
    }
    // Call setupHoldNXGesture() in your initListeners() or wherever appropriate
    // after player/binding is available.
}