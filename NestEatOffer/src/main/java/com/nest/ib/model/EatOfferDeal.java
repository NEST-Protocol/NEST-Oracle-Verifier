package com.nest.ib.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author wll
 * @date 2020/8/3 11:29
 * Exchange trading
 */
public class EatOfferDeal implements Serializable {

    private static final long serialVersionUID = 20200820L;

    /**
     * Erc20 name
     */
    private String erc20TokenName;

    /**
     * Quotation miner address
     */
    private String owner;

    /**
     * Offer price hash
     */
    private String offerHash;

    /**
     * Recharge the transaction hash
     */
    private String dealHash;

    /**
     * Transaction order ID
     */
    private long orderId;

    /**
     * 0 No task;
     * 1. Task;
     * 2. Completed recharge to the corresponding wallet of the exchange;
     * 3. Completed sending orders of trading pairs
     */
    private int transactionStatus;

    /**
     * Name of token to be sold
     */
    private String sellTokenName;

    /**
     * Quantity sold
     */
    private BigDecimal sellTokenAmount;


    public String getErc20TokenName() {
        return erc20TokenName;
    }

    public String getSellTokenName() {
        return sellTokenName;
    }

    public void setSellTokenName(String sellTokenName) {
        this.sellTokenName = sellTokenName;
    }

    public void setErc20TokenName(String erc20TokenName) {
        this.erc20TokenName = erc20TokenName;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOfferHash() {
        return offerHash;
    }

    public void setOfferHash(String offerHash) {
        this.offerHash = offerHash;
    }

    public BigDecimal getSellTokenAmount() {
        return sellTokenAmount;
    }

    public void setSellTokenAmount(BigDecimal sellTokenAmount) {
        this.sellTokenAmount = sellTokenAmount;
    }

    public String getDealHash() {
        return dealHash;
    }

    public void setDealHash(String dealHash) {
        this.dealHash = dealHash;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public int getTransactionStatus() {
        return transactionStatus;
    }

    public void setTransactionStatus(int transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

}
