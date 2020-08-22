package com.nest.ib.utils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author wll
 * @date 2020/7/16 21:15
 * Digital manipulation tools class
 */
public class MathUtils {

    /**
     * BigInteger / BigDecimal
     */
    public static BigDecimal intDivDec(BigInteger bigInteger, BigDecimal decimal, int scale) {
        return toDecimal(bigInteger).divide(decimal, scale, BigDecimal.ROUND_DOWN);
    }

    /**
     * BigDecimal * BigInteger
     */
    public static BigDecimal decMulInt(BigDecimal decimal, BigInteger bigInteger) {
        return decimal.multiply(toDecimal(bigInteger));
    }

    /**
     * BigDecimal - BigInteger
     */
    public static BigDecimal decSubInt(BigDecimal decimal, BigInteger bigInteger) {
        return decimal.subtract(toDecimal(bigInteger));
    }

    /**
     * BigDecimal to BigInteger
     * @param decimal
     * @return
     */
    public static BigInteger toBigInt(BigDecimal decimal) {
        return new BigInteger(String.valueOf(decimal.setScale(0, BigDecimal.ROUND_DOWN)));
    }

    /**
     * BigInteger to BigDecimal
     * @param bigInteger
     * @return
     */
    public static BigDecimal toDecimal(BigInteger bigInteger) {
        return new BigDecimal(String.valueOf(bigInteger));
    }

    /**
     * BigInteger / BigInteger
     * @return
     */

    public static BigDecimal intDivInt(BigInteger bigInteger1, BigInteger bigInteger2, int scale) {
       return toDecimal(bigInteger1).divide(toDecimal(bigInteger2), scale, BigDecimal.ROUND_DOWN);
    }
}
