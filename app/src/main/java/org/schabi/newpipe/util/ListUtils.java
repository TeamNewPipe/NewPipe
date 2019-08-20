package org.schabi.newpipe.util;

import java.util.Comparator;
import java.util.List;

public class ListUtils {
    public static <T, P> int binarySearchUpperBound(List<T> ls, P target, Function<T, P> getter, Comparator<P> cmp) {
        int left = 0;
        int right = ls.size();

        while (left < right) {
            int mid = (left + right) / 2;

            if (cmp.compare(target, getter.apply(ls.get(mid))) > 0) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }

        return left;
    }
}
