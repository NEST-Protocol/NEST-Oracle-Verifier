package com.nest.ib.utils;

import com.nest.ib.constant.AddressEnum;
import com.nest.ib.constant.Constant;
import com.nest.ib.contract.ERC20;
import com.nest.ib.contract.Nest3OfferMain;
import com.nest.ib.contract.NestOfferPriceContract;
import com.nest.ib.contract.VoteContract;
import com.nest.ib.model.OfferContractData;
import com.nest.ib.service.EatOfferAndTransactionService;
import com.nest.ib.service.serviceImpl.EatOfferAndTransactionServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;


/**
 * @author wll
 * @date 2020/7/16 13:25
 * Operate Ethereum, get data, send transactions
 */
@Component
public class EthClient {
    private static final Logger LOG = LoggerFactory.getLogger(EthClient.class);

    @Autowired
    private EatOfferAndTransactionService transactionService;

    public Web3j web3j;
    public Credentials credentials;
    public ERC20 erc20;
    public NestOfferPriceContract nestOfferPriceContract;
    public Nest3OfferMain nest3OfferMain;

    // node
    public static String NODE;


    /**
     * Initializing node
     */
    public void initNode(String node) {
        web3j = Web3j.build(new HttpService(node));
        NODE = node;
    }


    /**
     * User private key update
     *
     * @param userPrivateKey
     */
    public void updateUserPrivateKey(String userPrivateKey) {
        credentials = Credentials.create(userPrivateKey);

        resetBean();
    }

    /**
     * Registered bean
     */
    public void resetBean() {
        if (credentials == null) {
            return;
        }

        // Mapping contract address
        mappingContractAddress();

        // Loading contract
        erc20 = ContractFactory.erc20(credentials, web3j);
        nestOfferPriceContract = ContractFactory.nestOfferPriceContract(credentials, web3j);
        nest3OfferMain = ContractFactory.nest3OfferMain(credentials, web3j);

        // Initializes the least eth
        if (!initLeastEth()) return;

        // Get ERC20 token information
        getErc20Info();

        // Gets the upper limit of the block interval, which is the time T0 required for the validation period
        setBlockLimit();

        // Check the authorization
        try {
            approveToOfferFactoryContract();
        } catch (Exception e) {
            LOG.error("ERC20 authorization for the quoted factory contract failed:{}", e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean initLeastEth() {
        // Base quotation scale
        BigInteger leastEth = null;
        // Eating bill poundage ratio
        BigInteger tranEth = null;
        try {
            leastEth = nest3OfferMain.checkleastEth().send();
            tranEth = nest3OfferMain.checkTranEth().send();
        } catch (Exception e) {
            LOG.error("Failed to obtain leastEth or tranEth,  please restart：{}", e.getMessage());
            return false;
        }

        if (leastEth == null || tranEth == null) return false;

        EatOfferAndTransactionServiceImpl.ONE_ETH_AMOUNT = leastEth;

        EatOfferAndTransactionServiceImpl.SERVICE_CHARGE = leastEth.multiply(tranEth).divide(BigInteger.valueOf(1000L));

        EatOfferAndTransactionServiceImpl.ONE_ETH_AMOUNT2 = leastEth.divide(MathUtils.toBigInt(Constant.UNIT_ETH));

        LOG.info("The leastEth obtains the success：{} ETH", EatOfferAndTransactionServiceImpl.ONE_ETH_AMOUNT2);
        return true;
    }

    /**
     * Get the wallet address
     *
     * @return
     */
    public String getAddress() {
        return credentials == null ? "Please fill in the correct private key first" : credentials.getAddress();
    }

    /**
     * Map the address of each contract before registering it
     */
    public void mappingContractAddress() {
        // NToken offer contract mapping
        if (!AddressEnum.USDT_TOKEN_CONTRACT_ADDRESS.getValue().equalsIgnoreCase(EatOfferAndTransactionServiceImpl.ERC20_TOKEN_ADDRESS)) {
            String nTokenFactoryAddress = mapping(credentials, web3j, "NToken quote factory", "nest.nToken.offerMain");
            AddressEnum.OFFER_CONTRACT_FACTORY_ADDRESS.setValue(nTokenFactoryAddress);
        } else {
            // NEST quote contract address
            String nestFactoryAddress = mapping(credentials, web3j, "Nest Quote Factory", "nest.v3.offerMain");
            AddressEnum.OFFER_CONTRACT_FACTORY_ADDRESS.setValue(nestFactoryAddress);
        }

        // Quotation contract
        String offerPriceAddress = mapping(credentials, web3j, "Quote the price", "nest.v3.offerPrice");
        AddressEnum.OFFER_PRICE_CONTRACT_ADDRESS.setValue(offerPriceAddress);
    }


    /**
     * Check whether one-time authorization has been conducted. If not, one-time authorization is conducted
     */
    public void approveToOfferFactoryContract() throws ExecutionException, InterruptedException, IOException {
        // Check the authorized amount
        BigInteger approveValue = allowance();
        BigInteger nonce = ethGetTransactionCount();
        // 1.5 times the authorization of gasPrice, which can be adjusted by itself
        BigInteger gasPrice = ethGasPrice().multiply(BigInteger.valueOf(15)).divide(BigInteger.TEN);

        if (approveValue.compareTo(new BigInteger("100000000000000")) <= 0) {

            List<Type> typeList = Arrays.<Type>asList(
                    new Address(AddressEnum.OFFER_CONTRACT_FACTORY_ADDRESS.getValue()),
                    new Uint256(new BigInteger("999999999999999999999999999999999999999999"))
            );

            Function function = new Function("approve", typeList, Collections.<TypeReference<?>>emptyList());
            String encode = FunctionEncoder.encode(function);
            String transaction = ethSendErc20RawTransaction(gasPrice, nonce, Constant.OFFER_GAS_LIMIT, BigInteger.ZERO, encode);
            LOG.info("One-time authorization hash：" + transaction);
        }
    }

    /**
     * The default gasPrice
     *
     * @return
     * @throws IOException
     */
    public BigInteger ethGasPrice() throws IOException {
        return web3j.ethGasPrice().send().getGasPrice();
    }

    /**
     * View the authorized amount
     *
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public BigInteger allowance() throws ExecutionException, InterruptedException {
        return erc20.allowance(credentials.getAddress(), AddressEnum.OFFER_CONTRACT_FACTORY_ADDRESS.getValue()).sendAsync().get();
    }

    /**
     * For the nonce value
     *
     * @return
     * @throws IOException
     */
    public BigInteger ethGetTransactionCount() throws IOException {
        return web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount();
    }

    /**
     * Get the latest ethereum block number
     *
     * @return
     * @throws IOException
     */
    public BigInteger ethBlockNumber() throws IOException {
        return web3j.ethBlockNumber().send().getBlockNumber();
    }

    /**
     * Obtain contract information from the contract address
     *
     * @param contractAddress
     * @return
     */
    public OfferContractData getPrice(String contractAddress) throws ExecutionException, InterruptedException {
        BigInteger index = nest3OfferMain.toIndex(contractAddress).sendAsync().get();
        String prieInfo = nest3OfferMain.getPrice(index).sendAsync().get();

        OfferContractData contractData = null;
        if (!StringUtils.isEmpty(prieInfo)) {
            contractData = transformToOfferContractData(prieInfo).get(0);
        }
        return contractData;
    }

    /**
     * Check the ETH balance in your wallet
     *
     * @return
     * @throws IOException
     */
    public BigInteger ethGetBalance() {
        try {
            return web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
        } catch (IOException e) {
            LOG.error("There is an exception to the ETH balance of the account:{}", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Query the ERC20 balance
     *
     * @return
     * @throws Exception
     */
    public BigInteger ethBalanceOfErc20() {
        try {
            return erc20.balanceOf(credentials.getAddress()).send();
        } catch (Exception e) {
            LOG.error("There is an exception for getting the balance of account:{}", EatOfferAndTransactionServiceImpl.SYMBOL, e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Find contract order information in reverse order
     *
     * @param count Maximum number of records returned
     * @return
     */
    public List<OfferContractData> list(BigInteger count) throws Exception {
        String contracts = nest3OfferMain.list(BigInteger.ZERO, count, BigInteger.ZERO).send();
        if (StringUtils.isEmpty(contracts)) {
            return null;
        }

        return transformToOfferContractData(contracts);
    }

    /**
     * Find contract information
     *
     * @param count
     * @param start
     * @return
     */
    public List<OfferContractData> find(BigInteger count, String start) throws Exception {
        if (nest3OfferMain == null || credentials == null) {
            return null;
        }

        String contracts = nest3OfferMain.find(start, count, Constant.MAX_FIND_COUNT, credentials.getAddress()).send();
        if (StringUtils.isEmpty(contracts)) {
            return null;
        }

        // The data returned by the Find interface is parsed
        List<OfferContractData> offerContractDataList = transformToOfferContractData(contracts);

        return offerContractDataList;
    }

    /**
     * Initiate order buying
     *
     * @param typeList   Parameter collection
     * @param payableEth Number of eth
     * @param gasPrice
     * @return
     */
    public String sendEatOffer(List typeList, BigInteger payableEth, String method, BigInteger gasPrice,BigInteger nonce) {
        Function function = new Function(method, typeList, Collections.<TypeReference<?>>emptyList());
        String encode = FunctionEncoder.encode(function);

        String transaction = null;
        try {
            transaction = ethSendRawTransaction(gasPrice, nonce, Constant.OFFER_GAS_LIMIT, payableEth, encode);
        } catch (Exception e) {
            LOG.error("The order fell through", e.getMessage());
            e.printStackTrace();
        }
        return transaction;
    }

    /**
     * Fetch quoted assets
     *
     * @return
     */
    public String turnOut(BigInteger nonce, List typeList, BigInteger gasPrice) {
        Function function = new Function("turnOut", typeList, Collections.<TypeReference<?>>emptyList());
        String encode = FunctionEncoder.encode(function);
        String transaction = null;
        try {
            transaction = ethSendRawTransaction(gasPrice, nonce, Constant.TURN_OUT_GAS_LIMIT, BigInteger.ZERO, encode);
        } catch (Exception e) {
            LOG.error("There is an exception for fetching quoted assets {}", e.getMessage());
            e.printStackTrace();
        }
        return transaction;
    }

    /**
     * Get the total number of quoted contracts
     */
    public BigInteger getPriceCount() throws Exception {
        return nest3OfferMain.getPriceCount().send();
    }

    /**
     * View transaction status
     */
    public TransactionReceipt ethGetTransactionReceipt(String hash) throws IOException {
        TransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt().get();
        return transactionReceipt;
    }


    /**
     * Send a deal
     */
    public EthSendTransaction ethSendRawTransaction(RawTransaction rawTransaction) throws ExecutionException, InterruptedException {
        byte[] signMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signMessage);
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).sendAsync().get();
        return ethSendTransaction;
    }


    /**
     * Converts the data returned by the contract into an object
     *
     * @param contracts
     * @return
     */
    private List<OfferContractData> transformToOfferContractData(String contracts) {
        List<OfferContractData> dataList = new ArrayList<>();

        String[] split = contracts.split(",");
        // The last one is empty
        int n = split.length - 1;
        String prefix = "0x";
        for (int i = 0; i < n; i += 9) {
            OfferContractData contractData = new OfferContractData();
            contractData.setUuid(prefix + split[i]);
            contractData.setOwner(prefix + split[i + 1]);
            contractData.setTokenAddress(prefix + split[i + 2]);
            contractData.setEthAmount(split[i + 3]);
            contractData.setTokenAmount(split[i + 4]);
            contractData.setDealEthAmount(split[i + 5]);
            contractData.setDealTokenAmount(split[i + 6]);
            contractData.setBlockNum(split[i + 7]);
            contractData.setServiceCharge(split[i + 8]);
            dataList.add(contractData);
        }

        return dataList;
    }

    /**
     * Send a deal
     */
    private String ethSendRawTransaction(BigInteger gasPrice, BigInteger nonce, BigInteger gasLimit, BigInteger payableEth, String encode) throws ExecutionException, InterruptedException {
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                AddressEnum.OFFER_CONTRACT_FACTORY_ADDRESS.getValue(),
                payableEth,
                encode);
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);
        String transactionHash = web3j.ethSendRawTransaction(hexValue).sendAsync().get().getTransactionHash();

        return transactionHash;
    }

    /**
     * Send the ERC20 transaction
     */
    private String ethSendErc20RawTransaction(BigInteger gasPrice, BigInteger nonce, BigInteger gasLimit, BigInteger payableEth, String encode) throws ExecutionException, InterruptedException {
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                EatOfferAndTransactionServiceImpl.ERC20_TOKEN_ADDRESS,
                payableEth,
                encode);
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        String transactionHash = web3j.ethSendRawTransaction(hexValue).sendAsync().get().getTransactionHash();
        return transactionHash;
    }


    /**
     * Mapping the contract
     *
     * @return
     */
    private String mapping(Credentials credentials1, Web3j web3j1, String addressName, String mappingName) {
        String address = null;
        try {
            VoteContract mappingContract = VoteContract.load(AddressEnum.VOTE_CONTRACT_ADDRESS.getValue(), web3j1, credentials1, Contract.GAS_PRICE, Contract.GAS_LIMIT);
            address = mappingContract.checkAddress(mappingName).sendAsync().get();
        } catch (Exception e) {
            LOG.error("{} contract address update failed:{}", addressName, e.getMessage());
            e.printStackTrace();
        }

        if (StringUtils.isEmpty(address) || address.equalsIgnoreCase(Address.DEFAULT.getValue())) {
            LOG.error("{} contract address update failed", addressName);
            return null;
        }

        LOG.info("{} contract address update:{}", addressName, address);
        return address;
    }

    /**
     * Set the validation block interval
     */
    private void setBlockLimit() {
        try {
            BigInteger blockLimit = nest3OfferMain.checkBlockLimit().send();
            transactionService.setBlockLimit(blockLimit);
        } catch (Exception e) {
            LOG.error("Failed to get block interval upper limit. Set the default value of 25:{}", e.getMessage());
            transactionService.setBlockLimit(Constant.DEFAULT_BLOCK_LIMIT);
            e.printStackTrace();
        }
    }

    /**
     * Get ERC20 token information
     */
    private void getErc20Info() {
        try {
            BigInteger decimal = erc20.decimals().send();
            long unit = (long) Math.pow(10, decimal.intValue());
            transactionService.setErc20Decimal(BigInteger.valueOf(unit));

            String symbol = erc20.symbol().send();

            //HBTC special treatment should be replaced by BTC
            if (symbol.equalsIgnoreCase("HBTC")) {
                symbol = "BTC";
            }

            String huobiApi = "https://api.huobi.pro/market/history/trade?size=1&symbol=";
            String url = huobiApi + EatOfferAndTransactionServiceImpl.SYMBOLS.toLowerCase();
            System.out.println(url);

            transactionService.setHuoBiApi(url);
            transactionService.setTokenSymbol(symbol);
            LOG.info("ERC20 scrip decimal places:  {} trading on:  eth{}", unit, symbol.toLowerCase());
        } catch (Exception e) {
            LOG.error("Failed to get the ERC20 token {decimal} and identifier {symbol}:{}", e.getMessage());
            e.printStackTrace();
        }
    }

}
