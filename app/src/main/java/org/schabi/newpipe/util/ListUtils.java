package org.schabi.newpipe.util;

import java.util.Comparator;
import java.util.List;

public class ListUtils {
    /**
     * Find upper bound with binary search, the computational complexity is O(log N)
     * @param ls        List of elements
     * @param target    The target we are searching for
     * @param getter    A function for retrieving the desired attribute from the elements in the list,
     *                  the return value will be used for comparisons. The getter could be an identity
     *                  function, x -> x.
     * @param cmp       The comparator for comparing elements in the list
     * @param <T>       The type of the elements in the list, should be the same as the input type of
     *                  the getter
     * @param <P>       The type of the target, should be the same as the output type of the getter
     * @return          The index of the upper bound
     */
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
