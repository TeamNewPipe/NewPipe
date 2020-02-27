package org.schabi.newpipe.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Utils {

    public static double round(double value, int places) {
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
