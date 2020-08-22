package com.nest.ib.constant;

/**
 * @author wll
 * @date 2020/7/20 10:54
 * Contract address
 */
public enum AddressEnum {

    /**
     * Offer price contract address
     */
    OFFER_PRICE_CONTRACT_ADDRESS(""),

    /**
     * Quotation Factory Contract
     */
    OFFER_CONTRACT_FACTORY_ADDRESS(""),

    /**
     * USDT contract address: Calibration quote type, unchangeable
     */
    USDT_TOKEN_CONTRACT_ADDRESS("0xdac17f958d2ee523a2206206994597c13d831ec7"),

    /**
     * Voting contract address: Maps each contract address and cannot be changed
     */
    VOTE_CONTRACT_ADDRESS("0x6Cd5698E8854Fb6879d6B1C694223b389B465dea")
    ;

    AddressEnum(String value) {
        this.value = value;
    }

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
