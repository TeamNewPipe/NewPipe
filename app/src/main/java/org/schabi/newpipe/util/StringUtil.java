package org.schabi.newpipe.util;

public class StringUtil {
	/**
	 *  Tests if a string is blank: null, empty, or only whitespace (" ", \r\n, \t, etc)
	 * @param string string to test
	 * @return if string is blank
	 */
	public static boolean isBlank(String string) {
		return string != null && !string.trim().isEmpty();
	}
}
