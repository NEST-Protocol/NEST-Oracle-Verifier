package com.nest.ib.constant;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author wll
 * @date 2020/7/16 13:22
 */
public interface Constant {

    /**
     * The multiplier of gasPrice of arbitrage quotation：15/10
     */
    BigInteger EAT_OFFER_GAS_PRICE_MULTIPLE = new BigInteger("15");

    /**
     * GasPrice multiplier of quotation launched when retrieving arbitrage：12/10
     */
    BigInteger TURNOUT_GAS_PRICE_MULTIPLE = new BigInteger("12");

    /**
     * The default block interval T0
     */
    BigInteger DEFAULT_BLOCK_LIMIT = new BigInteger("25");

    /**
     * The number of ETH required to eat a single quote (WEI)
     */
    BigInteger ONE_ETH_AMOUNT = new BigInteger("10000000000000000000");

    /**
     * Default miner packing fee
     */
    BigInteger PACKAGING_COSTS = new BigInteger("200000000000000000");

    /**
     * Order gasLimit
     */
    BigInteger OFFER_GAS_LIMIT = new BigInteger("2000000");

    /**
     * Fetch the transaction gasLimit
     */
    BigInteger TURN_OUT_GAS_LIMIT = new BigInteger("200000");

    /**
     * The ETH unit
     */
    BigDecimal UNIT_ETH = new BigDecimal("1000000000000000000");

    /**
     * Total amount of 10ETH quotation fee
     */
    BigInteger SERVICE_CHARGE = new BigInteger("10000000000000000");

    /**
     * If the price offset exceeds 10%, the minimum quotation amount should be multiplied by 10
     */
    BigDecimal OFFER_PRICE_OFFERSET = new BigDecimal("0.1");

    /**
     * The list interface has the maximum number of queries per session：100
     */
    BigInteger LIST_MAX_COUNT = BigInteger.valueOf(100L);

    /**
     * Gets the first query for an unretrieved asset contract
     */
    BigInteger FIRST_FIND_COUNT = BigInteger.valueOf(50L);

    /**
     * Find maxFindCount records up to 100 quote contracts at a time to find your own quote contract
     */
    BigInteger MAX_FIND_COUNT = BigInteger.valueOf(100L);
}
