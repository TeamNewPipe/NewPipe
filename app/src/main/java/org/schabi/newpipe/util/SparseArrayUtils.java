package org.schabi.newpipe.util;

import android.util.SparseArray;

public abstract class SparseArrayUtils {

	public static <T> void shiftItemsDown(SparseArray<T> sparseArray, int lower, int upper) {
		for (int i = lower + 1; i <= upper; i++) {
			final T o = sparseArray.get(i);
			sparseArray.put(i - 1, o);
			sparseArray.remove(i);
		}
	}

	public static <T> void shiftItemsUp(SparseArray<T> sparseArray, int lower, int upper) {
		for (int i = upper - 1; i >= lower; i--) {
			final T o = sparseArray.get(i);
			sparseArray.put(i + 1, o);
			sparseArray.remove(i);
		}
	}

	public static <T> int[] getKeys(SparseArray<T> sparseArray) {
		final int[] result = new int[sparseArray.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = sparseArray.keyAt(i);
		}
		return result;
	}
}
