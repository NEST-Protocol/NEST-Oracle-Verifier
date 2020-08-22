package com.nest.ib.model;


import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

/**
 * Quotation contract
 */
public class OfferThreeData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Address of Quotation Contract
     */
    private String contractAddress;

    /**
     * Quote ERC20 token name
     */
    private String erc20TokenName;

    /**
     * Quote ERC20 token address
     */
    private String erc20TokenAddress;

    /**
     * Quotation miner address
     */
    private String owner;

    /**
     * Block Number
     */
    private BigInteger blockNumber;

    /**
     * Verify the block interval at time T0
     */
    private Integer intervalBlock;

    /**
     * Quotation handling fee
     */
    private BigDecimal serviceCharge;


    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public String getErc20TokenName() {
        return erc20TokenName;
    }

    public void setErc20TokenName(String erc20TokenName) {
        this.erc20TokenName = erc20TokenName;
    }

    public String getErc20TokenAddress() {
        return erc20TokenAddress;
    }


    public void setErc20TokenAddress(String erc20TokenAddress) {
        this.erc20TokenAddress = erc20TokenAddress;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public BigInteger getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(BigInteger blockNumber) {
        this.blockNumber = blockNumber;
    }

    public Integer getIntervalBlock() {
        return intervalBlock;
    }

    public void setIntervalBlock(Integer intervalBlock) {
        this.intervalBlock = intervalBlock;
    }

    public BigDecimal getServiceCharge() {
        return serviceCharge;
    }

    public void setServiceCharge(BigDecimal serviceCharge) {
        this.serviceCharge = serviceCharge;
    }
}
