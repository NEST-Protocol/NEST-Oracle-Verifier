package com.nest.ib.service;
import java.math.BigInteger;
import java.util.Map;


/**
 * ClassName:EatOfferAndTransactionService
 * Description:
 */
public interface EatOfferAndTransactionService {

    /**
     *  Eat a single offer
     */
    void eatOffer();

    /**
     *  Retrieve the assets
     */
    void retrieveAssets();

    /**
     * Set block intervals
     * @param blockLimit
     */
    void setBlockLimit(BigInteger blockLimit);

    /**
     * Gets the validation period block interval
     * @return
     */
    BigInteger getBlockLimit();

    /**
     * Set the TOKNE decimal number
     * @param decimal
     */
    void setErc20Decimal(BigInteger decimal);

    /**
     * Set the fire coin API
     * @param url
     */
    void setHuoBiApi(String url);

    /**
     * Set token symbol
     * @param symbol
     */
    void setTokenSymbol(String symbol);

    /**
     * Get token symbol
     * @return
     */
    String getTokenSymbol();

    /**
     * Modify the trading status of Huocoin exchange
     * @return
     */
    boolean updateHuobiExchange();

    /**
     * Sets whether the exchange has user authentication on
     */
    void updateAuthorizedUser();

    /**
     * Change the exchange API-Key and API-Secret
     * @param apiKey
     * @param apiSecret
     * @return
     */
    String updateExchangeApiKey(String apiKey, String apiSecret);

    /**
     * The data that the user interface needs to display
     * @return
     */
    Map<String, String> eatOfferData();

    /**
     * Assets quoted on a sheet are traded on an exchange
     */
    void exchangeBuyAndSell();

    /**
     * Mention currency operation
     * @param
     */
    void getToken(String tokenName);
}
