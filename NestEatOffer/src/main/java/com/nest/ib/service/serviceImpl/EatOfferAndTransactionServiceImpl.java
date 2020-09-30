package com.nest.ib.service.serviceImpl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nest.ib.constant.Constant;
import com.nest.ib.contract.NestOfferPriceContract;
import com.nest.ib.model.EatOfferDeal;
import com.nest.ib.model.OfferContractData;
import com.nest.ib.model.OfferThreeData;
import com.nest.ib.service.EatOfferAndTransactionService;
import com.nest.ib.service.OfferThreeDataService;
import com.nest.ib.utils.EthClient;
import com.nest.ib.utils.HttpClientUtil;
import com.nest.ib.utils.MathUtils;
import com.nest.ib.utils.api.ApiClient;
import com.nest.ib.utils.api.JsonUtil;
import com.nest.ib.utils.request.CreateOrderRequest;
import com.nest.ib.utils.response.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple2;


import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * ClassName:EatOfferAndTransactionServiceImpl
 * Description:
 */
@Service
public class EatOfferAndTransactionServiceImpl implements EatOfferAndTransactionService {
    private static final Logger LOG = LoggerFactory.getLogger(EatOfferAndTransactionServiceImpl.class);

    // Validation period block interval
    private static volatile BigInteger BLOCK_LIMIT;

    // ERC20 precision
    private static volatile BigInteger DECIMAL = null;

    // Huocoin EXCHANGE API
    private static volatile String HUOBI_API = null;

    // ERC20 symbol
    public static volatile String SYMBOL = null;

    // Whether to enable verification eating order
    public static volatile boolean START_EAT = false;

    // Whether to start trading on huocoin exchange
    private volatile boolean START_HUOBI_EXCHANGE = false;

    // Whether the exchange has authenticated users
    private volatile String AUTHORIZED_USER = "true";

    /**
     * quotation fee
     */
    public static BigInteger SERVICE_CHARGE = null;

    /**
     * The number of ETH required to eat a single quote (WEI)
     */
    public static BigInteger ONE_ETH_AMOUNT = null;

    /**
     * The number of ETH required to eat a single quote (ETH)
     */
    public static BigInteger ONE_ETH_AMOUNT2 = null;

    private volatile String API_KEY = "";
    private volatile String API_SECRET = "";

    /**
     * To initiate a purchase order, the quotation price should be more than 2% off the exchange price
     */
    public static volatile BigDecimal EAT_PRICE_OFFERSET = new BigDecimal("0.02");

    // ERC20 address
    public static volatile String ERC20_TOKEN_ADDRESS;

    // Huocoin exchange trades pairs
    public static volatile String SYMBOLS;

    // All of the data saved is used for trading on the exchange
    private static List<EatOfferDeal> OFFER_DEAL_LIST = Collections.synchronizedList(new ArrayList<EatOfferDeal>());

    @Autowired
    private EthClient ethClient;
    @Autowired
    private OfferThreeDataService offerThreeDataService;


    /**
     * Quotation for single
     */
    @Override
    public void eatOffer() {
        if (!START_EAT) {
            LOG.info("Order verification is not enabled");
            return;
        }

        // Obtain contract order information for verification
        List<OfferThreeData> offerThreeDatas = offerThreeDataService.listOfferData();
        if (CollectionUtils.isEmpty(offerThreeDatas)) {
            LOG.info("There are currently no quotes pending validation");
            return;
        }

        BigInteger gasPrice = null;
        try {
            gasPrice = ethClient.ethGasPrice().multiply(Constant.EAT_OFFER_GAS_PRICE_MULTIPLE).divide(BigInteger.TEN);
        } catch (IOException e) {
            LOG.info("Eat order to obtain gasPrice anomaly: {}", e.getMessage());
            e.printStackTrace();
        }

        try {
            // Traverse these contracts to find those whose prices exceed the specified offset percentage
            for (OfferThreeData offerThreeData : offerThreeDatas) {
                // Access to contract information
                String contractAddress = offerThreeData.getContractAddress();
                OfferContractData contractData = ethClient.getPrice(contractAddress);
                if (contractData == null) {
                    LOG.error("No information related to this contract was found：", contractAddress);
                    continue;
                }

                // Get the exchange price
                BigDecimal exchangePrice = getExchangePrice();
                if (exchangePrice == null || exchangePrice.compareTo(BigDecimal.ZERO) == 0) {
                    LOG.error("Huocoin exchange price failed to obtain");
                    continue;
                }
                LOG.info("Exchange price：{}", exchangePrice);

                // Number of ETH remaining tradable
                BigInteger dealEthAmount = new BigInteger(contractData.getDealEthAmount());
                // The number of ERC20 remaining available
                BigInteger dealErc20Amount = new BigInteger(contractData.getDealTokenAmount());
                // Judge whether the offer contract meets the requirements of the order: surplus, profitable
                boolean canEat = canEat(contractAddress, dealEthAmount, dealErc20Amount, exchangePrice, offerThreeData);
                if (!canEat) continue;

                // Get the balance
                BigInteger ethBalance = ethClient.ethGetBalance();
                BigInteger erc20Balance = ethClient.ethBalanceOfErc20();
                LOG.info("Current account balance：ETH={}，{}={}", ethBalance, SYMBOL, erc20Balance);
                if (ethBalance == null || erc20Balance == null) {
                    return;
                }

                // The price of being eaten
                BigDecimal eatPrice = calPrice(dealErc20Amount, dealEthAmount);
                // Get eat haploid multiple
                BigInteger multiple = getOfferMultiple(exchangePrice, eatPrice);
                if (multiple == null) continue;

                // Determine the type of food eaten
                boolean eatEth = false;
                // The exchange price is higher than the order price: PAY ERC20, eat ETH
                if (exchangePrice.compareTo(eatPrice) > 0) {
                    eatEth = true;
                } else { // The exchange price is less than the order price: pay EHT, eat ERC20
                    eatEth = false;
                }

                // Decide if you can eat them all
                boolean canEatAll = canEatAll(multiple, exchangePrice, eatEth, dealEthAmount, dealErc20Amount, ethBalance, erc20Balance);
                BigInteger copies = null;
                // You can eat them all
                if (canEatAll) {
                    copies = dealEthAmount.divide(ONE_ETH_AMOUNT);
                } else {// You can't eat them all
                    // Serving number: 10ETH per serving
                    copies = getCopies(eatEth, exchangePrice, ethBalance, erc20Balance, eatPrice, multiple);
                    if (copies.compareTo(BigInteger.ZERO) <= 0) {
                        LOG.info("The balance is not enough to take the order");
                        return;
                    }
                }

                String hash = sendEatOffer(exchangePrice, eatPrice, multiple, eatEth, copies, contractAddress, gasPrice);
                Thread.sleep(1000 * 2);
            }
        } catch (Exception e) {
            LOG.error("Eating order abnormal：{}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send an order to trade
     *
     * @param exchangePrice   Exchange price
     * @param eatPrice        Eat a single price
     * @param multiple        A multiple
     * @param eatEth          Whether to eat the ETH
     * @param copies          Eat a single number
     * @param contractAddress A bill of lading contract
     * @param gasPrice
     */
    private String sendEatOffer(BigDecimal exchangePrice, BigDecimal eatPrice, BigInteger multiple,
                                boolean eatEth, BigInteger copies, String contractAddress, BigInteger gasPrice) {
        // Trade: the ETH
        BigInteger tranEthAmout = copies.multiply(ONE_ETH_AMOUNT);
        // Trade: ERC20
        BigInteger tranErc20Amount = MathUtils.toBigInt(MathUtils.decMulInt(eatPrice, copies.multiply(ONE_ETH_AMOUNT2)).multiply(MathUtils.toDecimal(DECIMAL)));
        // Quote: the ETH
        BigInteger offerEthAmount = tranEthAmout.multiply(multiple);
        // Quote: erc20
        BigInteger eth = MathUtils.toBigInt(MathUtils.toDecimal(offerEthAmount).divide(Constant.UNIT_ETH, 0, BigDecimal.ROUND_DOWN));
        BigInteger offerErc20Amount = MathUtils.toBigInt(MathUtils.decMulInt(exchangePrice, eth).multiply(MathUtils.toDecimal(DECIMAL)));
        // The service fee
        BigInteger serviceCharge = SERVICE_CHARGE.multiply(copies);
        // ETH charges: quotation, menu, service charge
        BigInteger payEthAmount = null;

        String msg = null;
        String method = null;
        EatOfferDeal offerDeal = new EatOfferDeal();
        // Eat the ETH
        if (eatEth) {
            msg = "EatEth menu (enter {} to get ETH) ，Hash ： {}";
            method = "sendErcBuyEth";
            payEthAmount = offerEthAmount.add(serviceCharge).subtract(tranEthAmout);

            offerDeal.setSellTokenName("eth");
            offerDeal.setSellTokenAmount(MathUtils.toDecimal(tranEthAmout));
        } else { // eat the ERC20
            msg = "EatErc eating order (enter ETH to get {}) Hash ： {}";
            method = "sendEthBuyErc";
            payEthAmount = offerEthAmount.add(serviceCharge).add(tranEthAmout);

            offerDeal.setSellTokenName(SYMBOL.toLowerCase());
            offerDeal.setSellTokenAmount(MathUtils.toDecimal(tranErc20Amount));
        }

        List<Type> typeList = Arrays.<Type>asList(
                new Uint256(offerEthAmount),
                new Uint256(offerErc20Amount),
                new Address(contractAddress),
                new Uint256(tranEthAmout),
                new Uint256(tranErc20Amount),
                new Address(ERC20_TOKEN_ADDRESS)
        );

        LOG.info("Eating list quotes: trading ETH={} trading {}={} quotes ETH={} quotes {}={}  payEthAmount={}",
                tranEthAmout, SYMBOL, tranErc20Amount, offerEthAmount, SYMBOL, offerErc20Amount, payEthAmount);

        // Initiate an order offer transaction
        String transactionHash = ethClient.sendEatOffer(typeList, payEthAmount, method, gasPrice);

        // Keep order buying and selling, then trade on an exchange
        if (!StringUtils.isEmpty(transactionHash)) {
            offerDeal.setOfferHash(transactionHash);
            offerDeal.setErc20TokenName(SYMBOL);
            offerDeal.setOwner(ethClient.credentials.getAddress());
            offerDeal.setTransactionStatus(1);

            OFFER_DEAL_LIST.add(offerDeal);
        }

        LOG.info(msg, SYMBOL, transactionHash);
        return transactionHash;
    }


    /**
     * Fetch quotation contract assets (over 25 blocks)
     */
    @Override
    public void retrieveAssets() {
        List<OfferContractData> offerContractAddresses = getOfferContractAddress();
        if (CollectionUtils.isEmpty(offerContractAddresses)) {
            LOG.info("There is no quotation contract that needs to be retrieved at present");
            return;
        }

        LOG.info("Unretrieved assets：====" + offerContractAddresses.size());
        BigInteger gasPrice = null;
        try {
            gasPrice = ethClient.ethGasPrice();
        } catch (IOException e) {
            LOG.error("There is an exception to the gasPrice obtained during fetch：" + e);
        }
        gasPrice = gasPrice.multiply(Constant.TURNOUT_GAS_PRICE_MULTIPLE).divide(BigInteger.TEN);

        BigInteger nonce = null;
        try {
            nonce = ethClient.ethGetTransactionCount();
        } catch (IOException e) {
            LOG.error("An exception occurs when you get a nonce on a fetch：" + e);
        }

        for (OfferContractData contractData : offerContractAddresses) {
            List<Type> typeList = Arrays.<Type>asList(new Address(contractData.getUuid()));
            String transaction = null;
            try {
                transaction = ethClient.turnOut(nonce, typeList, gasPrice);
            } catch (Exception e) {
                LOG.error("An exception has occurred to fetch the quote", e.getMessage());
            }
            nonce = nonce.add(BigInteger.ONE);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            LOG.info("hash： " + transaction);
        }
    }


    /**
     * The assets quoted by the purchase order are traded on the exchange :
     * 0 no task;
     * 1. There is a task;
     * 2. The recharge has been completed and sent to the corresponding wallet of the exchange;
     */
    @Override
    public void exchangeBuyAndSell() {

        // Check to see if the exchange is open
        if (!START_HUOBI_EXCHANGE) {
            return;
        }

        // Check to see if the exchange's API-Key and API-Secret are set
        if (API_KEY.equalsIgnoreCase("") || API_SECRET.equalsIgnoreCase("")) {
            LOG.info("If the assets need to be exchanged in the exchange after receiving the quotation, please set the EXCHANGE'S API-Key and open the charging permission");
            return;
        }

        // Initiate a withdrawal operation when there are no pending or pending transactions
        if (CollectionUtils.isEmpty(OFFER_DEAL_LIST)) {
            LOG.info("There are no pending or pending transactions");
            // The withdrawal operation is initiated at this point
            getToken("eth");
            getToken(EatOfferAndTransactionServiceImpl.SYMBOL.toLowerCase());
            return;
        }

        // Go through the order data and buy and sell
        for (EatOfferDeal offerDeal : OFFER_DEAL_LIST) {
            int transactionStatus = offerDeal.getTransactionStatus();
            switch (transactionStatus) {
                // Have a task, recharge
                case 1:
                    LOG.info("Received recharge to the task of the exchange");
                    recharge(offerDeal);
                    break;
                // Recharge has been completed, order is initiated
                case 2:
                    LOG.info("Into the buying and selling");
                    if (makeOrder(offerDeal)) return;
                    break;
            }
        }
    }

    // Mention currency operation
    @Override
    public void getToken(String BUY_TOKEN_NAME_EXCHANGE) {
        // Query the coin chain information
        QueryExtractServiceChargeResponse queryExtractServiceChargeResponse = apiReferenceCurrencies(BUY_TOKEN_NAME_EXCHANGE, AUTHORIZED_USER);
        // Enquire currency withdrawal fee
        String serviceCharge = queryExtractServiceCharge(BUY_TOKEN_NAME_EXCHANGE, queryExtractServiceChargeResponse);
        // Chain name, default Ethereum
        String chain = BUY_TOKEN_NAME_EXCHANGE.equalsIgnoreCase("usdt") ? "usdterc20" : "";
        ApiClient client = new ApiClient(API_KEY, API_SECRET);
        // Query the TOKEN balance to be extracted from the account
        HashMap<String, String> exchangeBalance = getExchangeBalance();
        if (CollectionUtils.isEmpty(exchangeBalance)) {
            LOG.info("All the assets of the exchange are empty, so the assets may not have been accounted for");
            return;
        }

        String balance = exchangeBalance.get(BUY_TOKEN_NAME_EXCHANGE);
        if (StringUtils.isEmpty(balance)) {
            LOG.info("{}:The balance is empty", BUY_TOKEN_NAME_EXCHANGE);
            return;
        }

        if (new BigDecimal(balance).compareTo(new BigDecimal("1")) <= 0) {
            LOG.info("There is no amount to withdraw");
            return;
        }

        // Amount of currency withdrawal :(order quantity - handling fee)
        BigDecimal getAmount = new BigDecimal(balance).subtract(new BigDecimal(serviceCharge));
        String amount = String.valueOf(getAmount);
        LOG.info("The amount of money =" + amount);
        // Send withdrawal request
        ExtractERC20Response extractERC20Response = client.extractERC20(
                ethClient.credentials.getAddress(),
                amount,
                BUY_TOKEN_NAME_EXCHANGE,
                serviceCharge,
                chain);
        long data = extractERC20Response.getData();
        if (data == 0) {
            LOG.info("The withdrawal is not successful. Please confirm whether the withdrawal address is set");
            return;
        }
        LOG.info("Withdrawal transaction no： " + data);
    }

    /**
     * Get the quote contract address that needs to be retrieved through the Find interface
     *
     * @return
     */
    private List<OfferContractData> getOfferContractAddress() {
        // All quotation data queried
        List<OfferContractData> contractList = new ArrayList<>();
        try {
            contractList = ethClient.find(Constant.FIRST_FIND_COUNT, Address.DEFAULT.getValue());
        } catch (Exception e) {
            LOG.error("The Find interface failed to get the quote contract:{}", e.getMessage());
            e.printStackTrace();
        }

        if (CollectionUtils.isEmpty(contractList)) {
            return Collections.EMPTY_LIST;
        }

        BigInteger nowBlockNumber = null;
        try {
            nowBlockNumber = ethClient.ethBlockNumber();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Unretrieved contract
        List<OfferContractData> list = new ArrayList<>();
        int index = contractList.size() - 1;
        for (int i = index; i >= 0; i--) {

            OfferContractData contractData = contractList.get(i);

            BigInteger tokenAmount = new BigInteger(contractData.getTokenAmount());
            BigInteger serviceCharge = new BigInteger(contractData.getServiceCharge());
            BigInteger ethAmount = new BigInteger(contractData.getEthAmount());
            BigInteger blockNumber = new BigInteger(contractData.getBlockNum());

            // Fetch is not available until after the validation period
            if (nowBlockNumber.subtract(blockNumber).compareTo(BLOCK_LIMIT) < 0) {
                continue;
            }

            // If the balance or service fee is greater than 0, the assets have not been retrieved
            if (tokenAmount.compareTo(BigInteger.ZERO) > 0
                    || serviceCharge.compareTo(BigInteger.ZERO) > 0
                    || ethAmount.compareTo(BigInteger.ZERO) > 0) {
                list.add(contractData);
            }
        }

        return list;
    }


    // Query order
    private OrdersDetailResponse ordersDetail(String orderId) {
        ApiClient client = new ApiClient(API_KEY, API_SECRET);
        OrdersDetailResponse ordersDetail = client.ordersDetail(orderId);
        return ordersDetail;
    }

    // Enquire currency withdrawal fee
    private String queryExtractServiceCharge(String currency, QueryExtractServiceChargeResponse queryExtractServiceChargeResponse) {
        if (currency == null) return null;
        List<QueryExtractServiceChargeChains> chains = queryExtractServiceChargeResponse.getData().get(0).getChains();
        if (currency.equalsIgnoreCase("usdt")) {
            for (QueryExtractServiceChargeChains chain : chains) {
                String chain1 = chain.getChain();
                if (chain1.equalsIgnoreCase("usdterc20")) {
                    String withdrawFeeType = chain.getWithdrawFeeType();
                    if (withdrawFeeType.equalsIgnoreCase("fixed")) {
                        return chain.getTransactFeeWithdraw();
                    }

                    if (withdrawFeeType.equalsIgnoreCase("circulated")) {
                        return chain.getMinTransactFeeWithdraw();
                    }

                    if (withdrawFeeType.equalsIgnoreCase("ratio")) {
                        return chain.getTransactFeeRateWithdraw();
                    }
                }
            }
        } else {
            QueryExtractServiceChargeChains queryExtractServiceChargeChains = chains.get(0);
            String withdrawFeeType = queryExtractServiceChargeChains.getWithdrawFeeType();
            if (withdrawFeeType.equalsIgnoreCase("fixed")) {
                return queryExtractServiceChargeChains.getTransactFeeWithdraw();
            }

            if (withdrawFeeType.equalsIgnoreCase("circulated")) {
                return queryExtractServiceChargeChains.getMinTransactFeeWithdraw();
            }

            if (withdrawFeeType.equalsIgnoreCase("ratio")) {
                return queryExtractServiceChargeChains.getTransactFeeRateWithdraw();
            }
        }
        return null;
    }

    // Initiate buy and sell order
    private boolean makeOrder(EatOfferDeal offerDeal) {
        BigDecimal UNIT_TOKEN;
        String sellTokenName = offerDeal.getSellTokenName();
        if (sellTokenName.equalsIgnoreCase("eth")) {
            UNIT_TOKEN = Constant.UNIT_ETH;
        } else {
            UNIT_TOKEN = MathUtils.toDecimal(DECIMAL);
        }

        // Gets all tokens that are assets of the exchange
        HashMap<String, String> exchangeBalance = getExchangeBalance();
        if (CollectionUtils.isEmpty(exchangeBalance)) {
            LOG.info("All the assets of the exchange are empty, so the assets may not have been accounted for");
            return true;
        }

        // The balance of assets to be sold
        String exchangeToken = exchangeBalance.get(sellTokenName);
        BigDecimal tokenBalance = new BigDecimal(exchangeToken).multiply(UNIT_TOKEN);
        // Whether the exchange assets are sufficient
        if (tokenBalance.compareTo(offerDeal.getSellTokenAmount()) >= 0) {
            // The order number
            long orderId = -1L;
            // Initiate buy and sell order
            if (sellTokenName.equalsIgnoreCase("eth")) {
                orderId = exchangeOperation(BigDecimal.ZERO, SYMBOL, offerDeal.getSellTokenAmount());
            } else {
                orderId = exchangeOperation(offerDeal.getSellTokenAmount(), "eth", BigDecimal.ZERO);
            }

            LOG.info("Buy and Sell order ID：" + orderId);
            if (orderId != -1L) {
                // The order has been completed. Delete the task from the collection
                OFFER_DEAL_LIST.remove(offerDeal);
            } else {
                LOG.info("Initiate order fail");
            }
        }
        return false;
    }

    // A prepaid phone
    private void recharge(EatOfferDeal offerDeal) {

        // Check to see if the purchase is complete
        TransactionReceipt transactionReceipt = null;
        try {
            transactionReceipt = ethClient.ethGetTransactionReceipt(offerDeal.getOfferHash());
        } catch (IOException e) {
            LOG.error("There is an error querying the transaction hash:{}", e.getMessage());
            return;
        }

        String status = transactionReceipt.getStatus();
        if (status.equalsIgnoreCase("0x1")) {

            // Carry out recharge to huocoin exchange
            String transactionHash = eatOfferRecharge(offerDeal.getSellTokenName(), MathUtils.toBigInt(offerDeal.getSellTokenAmount()));

            if (transactionHash != null) {
                LOG.info("It has been recharged to the exchange to trade hash：" + transactionHash);
                // Recharge completed modified status
                offerDeal.setTransactionStatus(2);
                offerDeal.setDealHash(transactionHash);
            }
        } else if (status.equalsIgnoreCase("0x0")) { // If the transaction fails, the task is deleted
            OFFER_DEAL_LIST.remove(offerDeal);
        }
    }

    /**
     * This node is used to query the static reference information (public data) of each currency and its block chain.
     */
    public QueryExtractServiceChargeResponse apiReferenceCurrencies(String currency, String authorizedUser) {
        ApiClient client = new ApiClient(API_KEY, API_SECRET);
        String s = client.queryExtractServiceCharge(currency, authorizedUser);
        JSONObject jsonObject = JSONObject.parseObject(s);
        int code = jsonObject.getIntValue("code");
        String message = jsonObject.getString("message");
        if (code != 200) {
            LOG.error("Query each currency static reference information exception：{}；{}", code, message);
            return null;
        }

        JSONArray data = jsonObject.getJSONArray("data");
        List<QueryExtractServiceChargeData> list = new ArrayList<>();

        data.forEach(da -> {
            JSONObject jsonObject1 = JSONObject.parseObject(String.valueOf(da));
            String currency1 = jsonObject1.getString("currency");
            String instStatus = jsonObject1.getString("instStatus");
            JSONArray chains = jsonObject1.getJSONArray("chains");
            List<QueryExtractServiceChargeChains> chainsList = new ArrayList<>();
            chains.forEach(tx -> {
                JSONObject jsonObject2 = JSONObject.parseObject(String.valueOf(tx));

                QueryExtractServiceChargeChains queryExtractServiceChargeChains = new QueryExtractServiceChargeChains();
                queryExtractServiceChargeChains.setChain(jsonObject2.getString("chain"));
                queryExtractServiceChargeChains.setDepositStatus(jsonObject2.getString("depositStatus"));
                queryExtractServiceChargeChains.setMaxTransactFeeWithdraw(jsonObject2.getString("maxTransactFeeWithdraw"));
                queryExtractServiceChargeChains.setMaxWithdrawAmt(jsonObject2.getString("maxWithdrawAmt"));
                queryExtractServiceChargeChains.setMinDepositAmt(jsonObject2.getString("minDepositAmt"));
                queryExtractServiceChargeChains.setMinTransactFeeWithdraw(jsonObject2.getString("minTransactFeeWithdraw"));
                queryExtractServiceChargeChains.setMinWithdrawAmt(jsonObject2.getString("minWithdrawAmt"));
                queryExtractServiceChargeChains.setNumOfConfirmations(jsonObject2.getString("numOfConfirmations"));
                queryExtractServiceChargeChains.setNumOfFastConfirmations(jsonObject2.getString("numOfFastConfirmations"));
                queryExtractServiceChargeChains.setWithdrawFeeType(jsonObject2.getString("withdrawFeeType"));
                queryExtractServiceChargeChains.setWithdrawPrecision(jsonObject2.getString("withdrawPrecision"));
                queryExtractServiceChargeChains.setWithdrawQuotaPerDay(jsonObject2.getString("withdrawQuotaPerDay"));
                queryExtractServiceChargeChains.setWithdrawQuotaPerYear(jsonObject2.getString("withdrawQuotaPerYear"));
                queryExtractServiceChargeChains.setWithdrawQuotaTotal(jsonObject2.getString("withdrawQuotaTotal"));
                queryExtractServiceChargeChains.setWithdrawStatus(jsonObject2.getString("withdrawStatus"));
                queryExtractServiceChargeChains.setTransactFeeWithdraw(jsonObject2.getString("transactFeeWithdraw"));
                queryExtractServiceChargeChains.setTransactFeeRateWithdraw(jsonObject2.getString("transactFeeRateWithdraw"));
                queryExtractServiceChargeChains.setInstStatus("instStatus");
                chainsList.add(queryExtractServiceChargeChains);
            });

            QueryExtractServiceChargeData queryExtractServiceChargeData = new QueryExtractServiceChargeData();
            queryExtractServiceChargeData.setChains(chainsList);
            queryExtractServiceChargeData.setCurrency(currency1);
            queryExtractServiceChargeData.setInstStatus(instStatus);
            list.add(queryExtractServiceChargeData);
        });

        QueryExtractServiceChargeResponse queryExtractServiceChargeResponse = new QueryExtractServiceChargeResponse();
        queryExtractServiceChargeResponse.setCode(code);
        queryExtractServiceChargeResponse.setData(list);
        return queryExtractServiceChargeResponse;
    }


    /**
     * Set the validation period block interval
     *
     * @param blockLimit
     */
    @Override
    public void setBlockLimit(BigInteger blockLimit) {
        BLOCK_LIMIT = blockLimit;
    }

    /**
     * Gets the validation period block interval
     *
     * @return
     */
    @Override
    public BigInteger getBlockLimit() {
        return BLOCK_LIMIT;
    }

    /**
     * Set the TOKNE decimal number
     *
     * @param decimal
     */
    @Override
    public void setErc20Decimal(BigInteger decimal) {
        DECIMAL = decimal;
    }

    /**
     * Set the fire coin API
     *
     * @param url
     */
    @Override
    public void setHuoBiApi(String url) {
        HUOBI_API = url;
    }

    /**
     * Modify the trading status of Huocoin exchange
     */
    @Override
    public boolean updateHuobiExchange() {
        if (START_HUOBI_EXCHANGE) {
            START_HUOBI_EXCHANGE = false;
        } else {
            START_HUOBI_EXCHANGE = true;
        }
        return START_HUOBI_EXCHANGE;
    }

    /**
     * Sets whether the exchange has user authentication on
     */
    @Override
    public void updateAuthorizedUser() {
        if (AUTHORIZED_USER.equalsIgnoreCase("true")) {
            AUTHORIZED_USER = "false";
        } else {
            AUTHORIZED_USER = "true";
        }
    }

    /**
     * Change the exchange API-Key and API-Secret
     */
    @Override
    public String updateExchangeApiKey(String apiKey, String apiSecret) {
        API_KEY = apiKey;
        API_SECRET = apiSecret;
        return "SUCCESS";
    }

    /**
     * The data that the user interface needs to display
     */
    @Override
    public Map<String, String> eatOfferData() {
        Map<String, String> jsonObject = new HashMap<>();

        if (API_KEY.equalsIgnoreCase("")) {
            jsonObject.put("apiKey", "Please fill out the API - KEY");
        } else {
            jsonObject.put("apiKey", "existing");
        }

        if (API_SECRET.equalsIgnoreCase("")) {
            jsonObject.put("apiSecret", "Please fill out the API - SECRET");
        } else {
            jsonObject.put("apiSecret", "existing");
        }

        jsonObject.put("huobiExchangeState", START_HUOBI_EXCHANGE + "");
        jsonObject.put("authorizedUser", AUTHORIZED_USER);
        return jsonObject;
    }


    /**
     * Set token symbol
     *
     * @param symbol
     */
    @Override
    public void setTokenSymbol(String symbol) {
        SYMBOL = symbol;
    }

    /**
     * Get token symbol
     *
     * @return
     */
    @Override
    public String getTokenSymbol() {
        return SYMBOL;
    }


    /**
     * Get single servings: 10ETH for one serving
     *
     * @param eatEth        Whether to eat the ETH
     * @param exchangePrice Exchange price
     * @param ethBalance    Balance of ETH
     * @param erc20Balance  ERC20 balance
     * @param eatPrice      Eat a single price
     * @param multiple      Eat a single multiple
     */
    private BigInteger getCopies(boolean eatEth, BigDecimal exchangePrice, BigInteger ethBalance, BigInteger erc20Balance, BigDecimal eatPrice, BigInteger multiple) {
        // Quoted ETH quantity
        BigInteger offerEth = multiple.multiply(ONE_ETH_AMOUNT);
        // Take a portion, ETH needed
        BigInteger eatOneEth = null;

        BigInteger ethCount = multiple.multiply(ONE_ETH_AMOUNT2);// Number of ETH
        BigInteger offerErc20 = MathUtils.toBigInt(MathUtils.decMulInt(exchangePrice, ethCount)).multiply(DECIMAL); // Offer
        BigInteger eatErc20 = MathUtils.toBigInt(MathUtils.decMulInt(eatPrice, ethCount)).multiply(DECIMAL); // Eat
        // Take a portion, ERC20 needed
        BigInteger eatOneErc20 = null;

        // Eat the ETH
        if (eatEth) {
            eatOneEth = offerEth.subtract(ONE_ETH_AMOUNT);
            eatOneErc20 = offerErc20.add(eatErc20);
        } else { // Eat the ERC20
            eatOneEth = offerEth.add(ONE_ETH_AMOUNT);
            eatOneErc20 = offerErc20.subtract(eatErc20);
        }

        // ETH balance can eat a single portion
        BigInteger balance = ethBalance.subtract(Constant.PACKAGING_COSTS).subtract(SERVICE_CHARGE);
        BigInteger copiesEth = MathUtils.toBigInt(MathUtils.intDivInt(balance, eatOneEth, 0));
        // The balance of ERC20 can be eaten as a single serving
        BigInteger copiesErc20 = MathUtils.toBigInt(MathUtils.intDivInt(erc20Balance, eatOneErc20, 0));

        if (copiesErc20.compareTo(copiesEth) < 0) {
            return copiesErc20;
        }
        return copiesEth;
    }

    /**
     * Check the balance of the order :0 insufficient balance; 1. Don't eat all; 2 You can eat them all
     *
     * @param multiple        A multiple
     * @param exchangePrice   Exchange price
     * @param eatEth          Whether to eat the ETH
     * @param dealEthAmount   ETH transaction amount
     * @param dealErc20Amount ERC20 transaction amount
     * @param ethBalance      Balance of ETH
     * @param erc20Balance    ERC20 balance
     */
    private boolean canEatAll(BigInteger multiple, BigDecimal exchangePrice, boolean eatEth,
                              BigInteger dealEthAmount, BigInteger dealErc20Amount,
                              BigInteger ethBalance, BigInteger erc20Balance) {
        // Eat all quoted ETH quantity
        BigInteger ethAmount = multiple.multiply(dealEthAmount);
        // Eat all quoted ERC20 quantity
        BigInteger eth = MathUtils.toBigInt(MathUtils.toDecimal(ethAmount).divide(Constant.UNIT_ETH, 0, BigDecimal.ROUND_DOWN));
        BigInteger erc20Amount = MathUtils.toBigInt(MathUtils.decMulInt(exchangePrice, eth)).multiply(DECIMAL);
        // Eat all the minimum number of ETH required
        BigInteger minEthAmount = null;
        // Eat the minimum amount of ERC20 required
        BigInteger minErc20Amount = null;
        // Eat the ETH
        if (eatEth) {
            // Eat all the minimum NUMBER of ETH: quote number + eating order handling fee + miners packing fee - eating order to get the ETH
            minEthAmount = ethAmount.add(SERVICE_CHARGE).add(Constant.PACKAGING_COSTS).subtract(dealEthAmount);
            // Eat all minimum REQUIRED ERC20 quantity: quote ERC20+ eat order required ERC20
            minErc20Amount = erc20Amount.add(dealErc20Amount);
        } else {// Eat ERC20
            // Eat all the minimum NUMBER of ETH: quoted number + eating order handling fee + miners packing fee + eating order required number of ETH
            minEthAmount = ethAmount.add(SERVICE_CHARGE).add(Constant.PACKAGING_COSTS).add(dealEthAmount);
            // Eat all minimum ERC20 quantity required: ETH* exchange price - the amount of ERC20 obtained by eating order
            minErc20Amount = erc20Amount.subtract(dealErc20Amount);
        }

        // You can't eat them all
        if (ethBalance.compareTo(minEthAmount) < 0 || erc20Balance.compareTo(minErc20Amount) < 0) {
            return false;
        }
        // Can eat all
        return true;
    }


    /**
     * The price is calculated by the amount of ERC20 and ETH
     *
     * @return
     */
    private BigDecimal calPrice(BigInteger erc20Amount, BigInteger ethAmount) {
        BigDecimal erc20Max = MathUtils.intDivDec(erc20Amount, MathUtils.toDecimal(DECIMAL), 18);
        BigDecimal ethMax = MathUtils.intDivDec(ethAmount, Constant.UNIT_ETH, 18);
        BigDecimal price = erc20Max.divide(ethMax, 18, BigDecimal.ROUND_DOWN);
        return price;
    }


    /**
     * Get the exchange price
     */
    private BigDecimal getExchangePrice() {
        if (HUOBI_API == null) {
            LOG.error("The Huobi API failed to initialize, and ERC20's Symbol failed to obtain");
            return null;
        }

        String s = HttpClientUtil.sendHttpGet(HUOBI_API);
        if (s == null) {
            return null;
        }

        JSONObject jsonObject = JSONObject.parseObject(s);
        JSONArray data = jsonObject.getJSONArray("data");
        if (data == null) {
            return null;
        }

        BigDecimal totalPrice = new BigDecimal("0");
        BigDecimal n = new BigDecimal("0");
        if (data.size() == 0) {
            return null;
        }

        for (int i = 0; i < data.size(); i++) {
            Object o = data.get(i);
            JSONObject jsonObject1 = JSONObject.parseObject(String.valueOf(o));
            JSONArray data1 = jsonObject1.getJSONArray("data");

            if (data1 == null) {
                continue;
            }

            if (data1.size() == 0) {
                continue;
            }

            JSONObject jsonObject2 = JSONObject.parseObject(String.valueOf(data1.get(0)));
            BigDecimal price = jsonObject2.getBigDecimal("price");
            if (price == null) {
                continue;
            }

            totalPrice = totalPrice.add(price);
            n = n.add(new BigDecimal("1"));
        }

        if (n.compareTo(new BigDecimal("0")) > 0) {
            totalPrice = totalPrice.divide(n, 18, BigDecimal.ROUND_DOWN);
            // Depending on the transaction pair, it needs to be processed differently, or converted to ethXXX if it ends in the ETH
            if (SYMBOLS.endsWith("eth")) {
                totalPrice = BigDecimal.ONE.divide(totalPrice, 18, BigDecimal.ROUND_DOWN);
            }
            return totalPrice;
        }
        return null;
    }


    /**
     * Get quote multiple
     *
     * @param exchangePrice Exchange price
     * @param eatPrice      Be eaten at a single price
     * @return
     */
    private BigInteger getOfferMultiple(BigDecimal exchangePrice, BigDecimal eatPrice) {
        // Current latest block price
        BigDecimal priceNow = checkPriceNow();
        if (priceNow == null) {
            LOG.error("The latest block price did not succeed");
            return null;
        }

        // Deviation of the exchange price from the latest effective quotation
        BigDecimal offset1 = (priceNow.subtract(exchangePrice)).divide(priceNow, 2, BigDecimal.ROUND_DOWN).abs();
        // Offset between the price being ordered and the latest effective offer
        BigDecimal offset2 = (priceNow.subtract(eatPrice)).divide(priceNow, 2, BigDecimal.ROUND_DOWN).abs();

        // The exchange price deviates more than 10% from the last effective quotation
        if (offset1.compareTo(Constant.OFFER_PRICE_OFFERSET) > 0) {
            // The price deviated from the latest effective quotation by more than 10%
            if (offset2.compareTo(Constant.OFFER_PRICE_OFFERSET) > 0) {
                // It only needs 2 times the quoted price
                return BigInteger.valueOf(2L);
            }
            // If the price of the order is not more than 10% deviation from the latest effective quotation, make a 10-fold quotation
            return BigInteger.TEN;
        }
        // 2 times the price
        return BigInteger.valueOf(2L);
    }

    /**
     * Get the latest valid price in the current contract
     */
    private BigDecimal checkPriceNow() {
        Tuple2<BigInteger, BigInteger> latestPrice = null;
        try {
            latestPrice = ethClient.nestOfferPriceContract.checkPriceNow(ERC20_TOKEN_ADDRESS).sendAsync().get();
        } catch (Exception e) {
            LOG.error("Get {} price through price contract exception :{}", SYMBOL, e.getMessage());
            e.printStackTrace();
        }
        BigInteger ethAmount = latestPrice.getValue1();
        BigInteger erc20Amount = latestPrice.getValue2();

        // The latest effective price in the price contract
        return calPrice(erc20Amount, ethAmount);
    }

    /**
     * Determine if the contract meets the conditions for ordering
     *
     * @param contractAddress Contract address
     * @param ethAmount       Remaining ETH amount
     * @param erc20Amount     The remaining ERC20 amount
     * @param coinPrice       Exchange price
     * @param offerThreeData
     * @return
     */
    private boolean canEat(String contractAddress, BigInteger ethAmount, BigInteger erc20Amount, BigDecimal coinPrice, OfferThreeData offerThreeData) {
        // Determine whether the tradable balance is greater than 0
        if (ethAmount.compareTo(BigInteger.ZERO) == 0 && erc20Amount.compareTo(BigInteger.ZERO) == 0) {
            LOG.info("The contract has been eaten up： " + offerThreeData.getContractAddress());
            return false;
        }

        // Compared with huocoin exchange price offset, more than the specified value on the order
        BigDecimal price = calPrice(erc20Amount, ethAmount);
        BigDecimal offset = (price.subtract(coinPrice)).divide(coinPrice, 4, BigDecimal.ROUND_DOWN).abs();
        if (offset.compareTo(EAT_PRICE_OFFERSET) < 0) {
            return false;
        }

        LOG.info("{}  The remaining ETH that can be traded：" + ethAmount + "    The rest can be traded {}：" + erc20Amount, contractAddress, SYMBOL);
        return true;
    }

    /**
     * Eat sheet quoted recharge
     *
     * @return
     */
    private String eatOfferRecharge(String sellTokenName, BigInteger sellTokenAmount) {
        /**
         *  Get the token exchange token corresponding to the address of the recharge wallet
         */
        String currency = sellTokenName;
        ApiClient client = new ApiClient(API_KEY, API_SECRET);
        DepositAddressResponse depositAddress = client.getDepositAddress(currency);
        int code = depositAddress.getCode();
        if (code != 200) {
            LOG.info("Fire coin exchange failed to query recharge address");
            return null;
        }

        String exchangeTokenAddress = depositAddress.getData().get(0).getAddress();
        LOG.info("Address of exchange charging currency：" + exchangeTokenAddress);
        // Ethereum addresses are 42 bits long and begin with 0x (the ethereum that the exchange queries defaults to first, making sure to prevent changes)
        if (exchangeTokenAddress.length() != 42 || !exchangeTokenAddress.substring(0, 2).equalsIgnoreCase("0x")) {
            LOG.info("Error querying the exchange address, the address found is：" + exchangeTokenAddress);
            return null;
        }

        // Query balance
        BigInteger balance = BigInteger.ZERO;
        try {
            if (currency.equalsIgnoreCase("eth")) {
                balance = ethClient.ethGetBalance();
            } else {
                balance = ethClient.ethBalanceOfErc20();
            }
        } catch (Exception e) {
            LOG.info("Query ETH balance failed, please check network status");
            return null;
        }

        // Check if the balance is sufficient
        if (balance.compareTo(sellTokenAmount) < 0) {
            LOG.info("Fund is insufficient, still cannot transfer to address of exchange account, check whether there is quotation contract that has not got back");
            return null;
        }

        String transactionHash;
        // Recharge assets to the exchange
        if (currency.equalsIgnoreCase("eth")) {
            LOG.info("top-up：" + exchangeTokenAddress + "  amount：" + sellTokenAmount);
            transactionHash = transferEth(exchangeTokenAddress, sellTokenAmount);
        } else {
            LOG.info("top-up：" + ERC20_TOKEN_ADDRESS + "  Exchange wallet address：" + exchangeTokenAddress + "  amount：" + sellTokenAmount);
            transactionHash = transferErc20(exchangeTokenAddress, sellTokenAmount);
        }

        return transactionHash;
    }

    /**
     * Fire currency exchange -> for trading operations
     *
     * @return
     */
    private Long exchangeOperation(BigDecimal rightTokenAmount, String buyTokenName, BigDecimal leftTokenAmount) {
        Long orderId = -1L;
        // Buy ETH, sell ERC20
        if (buyTokenName.equalsIgnoreCase("ETH")) {
            if (SYMBOLS.endsWith("eth")) {
                orderId = sendSellMarketOrder(SYMBOLS, String.valueOf(rightTokenAmount.divide(MathUtils.toDecimal(DECIMAL), 2, BigDecimal.ROUND_DOWN)));
            } else {
                orderId = sendBuyMarketOrder(SYMBOLS, String.valueOf(rightTokenAmount.divide(MathUtils.toDecimal(DECIMAL), 2, BigDecimal.ROUND_DOWN)));
            }
        } else {
            // Buy ERC20, sell ETH
            if (SYMBOLS.endsWith("eth")) {
                orderId = sendBuyMarketOrder(SYMBOLS, String.valueOf(leftTokenAmount.divide(Constant.UNIT_ETH, 2, BigDecimal.ROUND_DOWN)));
            } else {
                orderId = sendSellMarketOrder(SYMBOLS, String.valueOf(leftTokenAmount.divide(Constant.UNIT_ETH, 2, BigDecimal.ROUND_DOWN)));
            }
        }

        return orderId;
    }

    // Market selling order (e.g. trade to HTUSDT, sell HT to get USDT)
    private Long sendSellMarketOrder(String symbol, String amount) {
        ApiClient client = new ApiClient(API_KEY, API_SECRET);
        AccountsResponse accounts = client.accounts();
        Long orderId = -1L;
        List<Accounts> list = (List<Accounts>) accounts.getData();
        Accounts account = list.get(0);
        long accountId = account.getId();

        // create order:
        CreateOrderRequest createOrderReq = new CreateOrderRequest();
        createOrderReq.accountId = String.valueOf(accountId);
        createOrderReq.amount = amount;
        createOrderReq.symbol = symbol;
        createOrderReq.type = CreateOrderRequest.OrderType.SELL_MARKET; // The market price to sell
        createOrderReq.source = "api";

        //------------------------------------------------------ Create the order  -------------------------------------------------------
        try {
            orderId = client.createOrder(createOrderReq);
        } catch (Exception e) {
            LOG.info("There is an exception to the original sell order");
            return -1L;
        }

        LOG.info("Order ID: " + orderId);
        return orderId;
    }

    /**
     * Market order (e.g., trading to HTUSDT, buying TO HT and selling to USDT)
     */
    private Long sendBuyMarketOrder(String symbol, String amount) {
        ApiClient client = new ApiClient(API_KEY, API_SECRET);
        AccountsResponse accounts = client.accounts();
        Long orderId = -1L;
        List<Accounts> list = (List<Accounts>) accounts.getData();
        Accounts account = list.get(0);
        long accountId = account.getId();

        // create order:
        CreateOrderRequest createOrderReq = new CreateOrderRequest();
        createOrderReq.accountId = String.valueOf(accountId);
        createOrderReq.amount = amount;
        createOrderReq.symbol = symbol;
        createOrderReq.type = CreateOrderRequest.OrderType.BUY_MARKET; // The market price to sell
        createOrderReq.source = "api";

        //------------------------------------------------------ Create the order  -------------------------------------------------------
        try {
            orderId = client.createOrder(createOrderReq);
        } catch (Exception e) {
            LOG.info("Buy and buy abnormal");
            return -1L;
        }

        LOG.info("Order ID: " + orderId);
        return orderId;
    }


    /**
     * Query the exchange's token balance
     *
     * @return
     */
    private HashMap<String, String> getExchangeBalance() {
        ApiClient client = new ApiClient(API_KEY, API_SECRET);
        AccountsResponse accounts = client.accounts();
        List<Accounts> listAccounts = (List<Accounts>) accounts.getData();
        if (!listAccounts.isEmpty()) {
            //------------------------------------------------------ The account balance  -------------------------------------------------------
            BalanceResponse balance = client.balance(String.valueOf(listAccounts.get(0).getId()));

            String s = "";
            try {
                s = JsonUtil.writeValue(balance);
            } catch (IOException e) {
                e.printStackTrace();
            }

            JSONObject jsonObject = JSONObject.parseObject(s);
            String data = jsonObject.getString("data");
            JSONObject jsonObject1 = JSONObject.parseObject(data);
            JSONArray list = jsonObject1.getJSONArray("list");
            HashMap<String, String> hashMap = new HashMap();

            list.forEach(li -> {
                JSONObject jsonObject2 = JSONObject.parseObject(String.valueOf(li));
                String balanceStr = jsonObject2.getString("balance");
                String currency = jsonObject2.getString("currency").toLowerCase();
                if (!balanceStr.equalsIgnoreCase("0")) {
                    if (hashMap.containsKey(currency)) {
                        // The same token may appear several times, and select the one with the largest amount
                        if (new BigDecimal(hashMap.get(currency)).compareTo(new BigDecimal(balanceStr)) < 0) {
                            hashMap.replace(currency, hashMap.get(currency), balanceStr);
                        }
                    } else {
                        hashMap.put(currency, balanceStr);
                    }
                }
            });
            LOG.info("All balances in the exchange account：" + hashMap);
            return hashMap;
        }
        return null;
    }

    // Initiate ETH transaction
    private String transferEth(String address, BigInteger value) {
        try {
            BigInteger nonce = ethClient.ethGetTransactionCount();
            BigInteger gasPrice = ethClient.ethGasPrice();
            gasPrice = gasPrice.multiply(new BigInteger("3"));
            BigInteger gasLimit = new BigInteger("200000");
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, address, value);
            EthSendTransaction ethSendTransaction = ethClient.ethSendRawTransaction(rawTransaction);
            return ethSendTransaction.getTransactionHash();
        } catch (Exception e) {
            return null;
        }

    }

    // Initiate AN ERC20 token transaction
    private String transferErc20(String address, BigInteger value) {
        try {
            BigInteger nonce = ethClient.ethGetTransactionCount();
            BigInteger gasPrice = ethClient.ethGasPrice();
            gasPrice = gasPrice.multiply(new BigInteger("3"));
            BigInteger gasLimit = new BigInteger("200000");
            if (address != null) {
                final Function function = new Function(
                        "transfer",
                        Arrays.<Type>asList(new Address(address),
                                new Uint256(value)),
                        Collections.<TypeReference<?>>emptyList());
                String encode = FunctionEncoder.encode(function);
                RawTransaction rawTransaction = RawTransaction.createTransaction(
                        nonce,
                        gasPrice,
                        gasLimit,
                        ERC20_TOKEN_ADDRESS,
                        encode);

                String transactionHash = ethClient.ethSendRawTransaction(rawTransaction).getTransactionHash();
                return transactionHash;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
