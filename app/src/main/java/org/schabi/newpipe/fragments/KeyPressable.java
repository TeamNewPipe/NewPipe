package org.schabi.newpipe.fragments;

import android.view.KeyEvent;

/**
 * Indicates that the current fragment can handle key presses
 */
public interface KeyPressable {
	/**
	 * A back press was delegated to this fragment
	 *
	 * @return if the back press was handled
	 */
	boolean onKeyPressed(int keyCode, KeyEvent event);
}
