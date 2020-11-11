[toc]

***

### NEST3.0 Automatic order taking arbitrage program

***

#### 1. Create private key, node

>Since calling the contract to obtain relevant data and send transactions, you need to interact with the chain, and you need to prepare an Ethereum node URL and private key. The node can apply for free after registration through https://infura.io/ or https://quiknode.io/.
>

```java
// Ethereum node
String ETH_NODE = "";
// Private key
String USER_PRIVATE_KEY = "";
Web3j web3j = Web3j.build(new HttpService(ETH_NODE));
Credentials credentials = Credentials.create(USER_PRIVATE_KEY);
```

#### 2. Get Nest Protocol related contract address

>The role of mapping contracts in Nest Protocol: manage all other contract addresses.
>
>The contracts involved in the quotation are: ERC20 token contract, mapping contract, quotation contract, mining pool contract, and price contract.

```java
// nToken quotation contract mapping
if (!AddressEnum.USDT_TOKEN_CONTRACT_ADDRESS.getValue().equalsIgnoreCase(EatOfferAndTransactionServiceImpl.ERC20_TOKEN_ADDRESS)) {
    String nTokenFactoryAddress = mapping(credentials, web3j, "nToken quotation factory", "nest.nToken.offerMain");
    AddressEnum.OFFER_CONTRACT_FACTORY_ADDRESS.setValue(nTokenFactoryAddress);
} else {
    // NEST quote contract address
    String nestFactoryAddress = mapping(credentials, web3j, "nest quotation factory", "nest.v3.offerMain");
    AddressEnum.OFFER_CONTRACT_FACTORY_ADDRESS.setValue(nestFactoryAddress);
}

// Quote price contract
String offerPriceAddress = mapping(credentials, web3j, "Quote the price", "nest.v3.offerPrice");
AddressEnum.OFFER_PRICE_CONTRACT_ADDRESS.setValue(offerPriceAddress);
```

#### 3. Authorized quotation contract ERC20

>Arbitrage needs to transfer ERC20 to the quotation contract. The transfer to ERC20 is performed by the quotation contract calling the ERC20 token contract, so ERC20 authorization is required for the quotation contract.

```java
// Check the approved amount
BigInteger approveValue = allowance();
BigInteger nonce = ethGetTransactionCount();
// 1.5 times gasPrice, adjustable
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
```

#### 4. Get the latest 100 quotation  directly for verification through the quotation contract list interface

```java
// Get the latest 100 quotation
List<OfferContractData> list = null;
try {
    list = ethClient.list(Constant.LIST_MAX_COUNT);
} catch (Exception e) {
    LOG.error("list interface failed to obtain contract data：{}", e.getMessage());
    e.printStackTrace();
}

// Traverse the obtained 100 contracts and find the contracts that are in the verification period and whose verifiable amount is greater than 0
List<OfferThreeData> dataList = new ArrayList<>();
int size = list.size();
for (int i = 0; i < size; i++) {
    OfferContractData t = list.get(i);

    //nToken take a single verification, only verify the specified ERC20token
    if (!EatOfferAndTransactionServiceImpl.ERC20_TOKEN_ADDRESS.equalsIgnoreCase(t.getTokenAddress())) {
        continue;
    }

    OfferThreeData offerThreeData = new OfferThreeData();
    offerThreeData.setContractAddress(t.getUuid());
    offerThreeData.setErc20TokenAddress(t.getTokenAddress());
    offerThreeData.setErc20TokenName(transactionService.getTokenSymbol());
    offerThreeData.setOwner(t.getOwner());
    offerThreeData.setBlockNumber(new BigInteger(t.getBlockNum()));
    offerThreeData.setIntervalBlock(transactionService.getBlockLimit().intValue());
    offerThreeData.setServiceCharge(new BigDecimal(t.getServiceCharge()));

    // Determine whether 25 blocks have passed
    if (blockNumber.subtract(offerThreeData.getBlockNumber()).intValue() >= 25) {
        continue;
    }

    //Determine whether to get it back through whether the balance and handling fee are zero
    BigDecimal ethAmount = new BigDecimal(t.getEthAmount());
    BigDecimal erc20Amount = new BigDecimal(t.getTokenAmount());
    if (ethAmount.compareTo(BigDecimal.ZERO) <= 0
            && erc20Amount.compareTo(BigDecimal.ZERO) <= 0
            && offerThreeData.getServiceCharge().compareTo(BigDecimal.ZERO) <= 0) {
        continue;
    }

    //Determine whether the remaining tradeable balance is 0,
    BigDecimal dealEth = new BigDecimal(t.getDealEthAmount());
    BigDecimal dealErc20 = new BigDecimal(t.getDealTokenAmount());
    if (dealEth.compareTo(BigDecimal.ZERO) <= 0 && dealErc20.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
    }
    dataList.add(offerThreeData);
}
return dataList;
}
```

#### 5. Get the current ERC20-ETH price of the exchange

>Get the price of ERC20-ETH through the exchange API with high trading frequency.
>
>Some exchange APIs require overseas nodes to access, the following is the Huobi Exchange API:

```java
if (HUOBI_API == null) {
    LOG.error("Huobi API failed to initialize successfully, and ERC20 symbol failed to get");
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
    // Need to do different processing according to the transaction pair, if it is the end of eth, it must be converted to ethxxx
    if (SYMBOLS.endsWith("eth")) {
        totalPrice = BigDecimal.ONE.divide(totalPrice, 18, BigDecimal.ROUND_DOWN);
    }
    return totalPrice;
}
return null;
```

#### 6. Initiate an arbitrage transaction

>Price of the quotation to be verified: the ratio of the ETH quantity to the ERC20 quantity of the quotation to be verified.
>
>The current exchange price is compared with the price of the quotation to be verified, and if the deviation meets expectations, arbitrage can be carried out.
>
>Note: If the price obtained by the current exchange and the latest effective price of the price contract deviate by more than 10%, then the two-way assets (ETH and ERC20) of the arbitrage initiated at the same time must be 10 times.

#####1. Check whether there is an arbitrage opportunity in the quoted contract. If there is an arbitrage transaction, you must quote twice the amount of arbitrage ETH.

```java
// Iterate through these contracts to find the contract whose price exceeds the specified offset percentage
for (OfferThreeData offerThreeData : offerThreeDatas) {
    // Get contract information
    String contractAddress = offerThreeData.getContractAddress();
    OfferContractData contractData = ethClient.getPrice(contractAddress);
    if (contractData == null) {
        LOG.error("未Find the relevant information of the contract：", contractAddress);
        continue;
    }

    // Get the exchange price
    BigDecimal exchangePrice = getExchangePrice();
    if (exchangePrice == null || exchangePrice.compareTo(BigDecimal.ZERO) == 0) {
        LOG.error("Huobi exchange price acquisition failed");
        continue;
    }
    LOG.info("Exchange price：{}", exchangePrice);

    // The remaining tradeable ETH quantity
    BigInteger dealEthAmount = new BigInteger(contractData.getDealEthAmount());
    // Remaining tradable ERC20 quantity
    BigInteger dealErc20Amount = new BigInteger(contractData.getDealTokenAmount());
    // Determine whether the quotation contract meets the conditions for taking orders: there is surplus and profitable
    boolean canEat = canEat(contractAddress, dealEthAmount, dealErc20Amount, exchangePrice, offerThreeData);
    if (!canEat) continue;

    // Get balance
    BigInteger ethBalance = ethClient.ethGetBalance();
    BigInteger erc20Balance = ethClient.ethBalanceOfErc20();
    LOG.info("Current account balance：ETH={}，{}={}", ethBalance, SYMBOL, erc20Balance);
    if (ethBalance == null || erc20Balance == null) {
        return;
    }

    // The price of the quilt
    BigDecimal eatPrice = calPrice(dealErc20Amount, dealEthAmount);
    // Get single multiple
    BigInteger multiple = getOfferMultiple(exchangePrice, eatPrice);
    if (multiple == null) continue;

    // Determine the type of order
    boolean eatEth = false;
    // The exchange price is greater than the price of the taken order: pay ERC20, eat ETH
    if (exchangePrice.compareTo(eatPrice) > 0) {
        eatEth = true;
    } else { // The exchange price is less than the price of the order being taken: pay EHT, eat ERC20
        eatEth = false;
    }

    // Judge whether you can eat all
    boolean canEatAll = canEatAll(multiple, exchangePrice, eatEth, dealEthAmount, dealErc20Amount, ethBalance, erc20Balance);
    BigInteger copies = null;
    // Can eat all
    if (canEatAll) {
        copies = dealEthAmount.divide(Constant.ONE_ETH_AMOUNT);
    } else {//Can't eat all
        // 10ETH one unit
        copies = getCopies(eatEth, exchangePrice, ethBalance, erc20Balance, eatPrice, multiple);
        if (copies.compareTo(BigInteger.ZERO) <= 0) {
            LOG.info("Insufficient balance to take orders");
            return;
        }
    }

    String hash = sendEatOffer(exchangePrice, eatPrice, multiple, eatEth, copies, contractAddress, gasPrice);
    Thread.sleep(1000 * 2);
}
```

#####2. Get the latest verified price of the price contract and judge the price deviation.

```java
// Current latest block price
BigDecimal priceNow = checkPriceNow();
if (priceNow == null) {
    LOG.error("The current latest block price has not been successfully obtained");
    return null;
}

// The offset between the exchange price and the latest effective quotation
BigDecimal offset1 = (priceNow.subtract(exchangePrice)).divide(priceNow, 2, BigDecimal.ROUND_DOWN).abs();
// The offset between the price of the taken order and the latest effective quote
BigDecimal offset2 = (priceNow.subtract(eatPrice)).divide(priceNow, 2, BigDecimal.ROUND_DOWN).abs();

// The exchange price deviates from the latest effective price by more than 10%
if (offset1.compareTo(Constant.OFFER_PRICE_OFFERSET) > 0) {
    // The price of the order being taken deviates from the latest effective price by more than 10%
    if (offset2.compareTo(Constant.OFFER_PRICE_OFFERSET) > 0) {
        // Need 2 times quote
        return BigInteger.valueOf(2L);
    }
    // If the price of the taken order does not deviate by more than 10% from the latest effective quotation, a 10 times quotation will be made
    return BigInteger.TEN;
}
// 2 times quote
return BigInteger.valueOf(2L);
```

#####3.Initiate a take-order quotation transaction, and save the take-order data for exchange trading after the transaction is successful.

```java
// take：ETH
BigInteger tranEthAmout = copies.multiply(Constant.ONE_ETH_AMOUNT);
// take：ERC20
BigInteger tranErc20Amount = MathUtils.toBigInt(MathUtils.decMulInt(eatPrice, copies.multiply(BigInteger.TEN)).multiply(MathUtils.toDecimal(DECIMAL)));
// quote：ETH
BigInteger offerEthAmount = tranEthAmout.multiply(multiple);
// quote：erc20
BigInteger eth = MathUtils.toBigInt(MathUtils.toDecimal(offerEthAmount).divide(Constant.UNIT_ETH, 0, BigDecimal.ROUND_DOWN));
BigInteger offerErc20Amount = MathUtils.toBigInt(MathUtils.decMulInt(exchangePrice, eth).multiply(MathUtils.toDecimal(DECIMAL)));
// fee
BigInteger serviceCharge = Constant.SERVICE_CHARGE.multiply(copies);
// ETH fees: quotation, take orders fees
BigInteger payEthAmount = null;


String msg = null;
String method = null;

EatOfferDeal offerDeal = new EatOfferDeal();
// take ETH
if (eatEth) {
    msg = "eatEthtakeorders(transfer{}ObtainETH) ，Hash ： {}";
    method = "sendErcBuyEth";
    payEthAmount = offerEthAmount.add(serviceCharge).subtract(tranEthAmout);

    offerDeal.setSellTokenName("eth");
    offerDeal.setSellTokenAmount(MathUtils.toDecimal(tranEthAmout));
} else { // take ERC20
    msg = "eatErctakeorders(transferETHObtain{}) Hash ： {}";
    method = "sendEthBuyErc";
    payEthAmount = offerEthAmount.add(serviceCharge).add(tranEthAmout);

    offerDeal.setSellTokenName(SYMBOL.toLowerCase());
    offerDeal.setSellTokenAmount(MathUtils.toDecimal(tranErc20Amount));
}

//Initiate transaction
List<Type> typeList = Arrays.<Type>asList(
        new Uint256(offerEthAmount),
        new Uint256(offerErc20Amount),
        new Address(contractAddress),
        new Uint256(tranEthAmout),
        new Uint256(tranErc20Amount),
        new Address(ERC20_TOKEN_ADDRESS)
);
LOG.info("Take orders quotations：takeETH={}  take{}={}  quoteETH={}  quote{}={}  payEthAmount={}",
        tranEthAmout, SYMBOL, tranErc20Amount, offerEthAmount, SYMBOL, offerErc20Amount, payEthAmount);

String transactionHash = ethClient.sendEatOffer(typeList, payEthAmount, method, gasPrice);

//Save the take orders, and then trade on the exchange
if (!StringUtils.isEmpty(transactionHash)) {
    offerDeal.setOfferHash(transactionHash);
    offerDeal.setErc20TokenName(SYMBOL);
    offerDeal.setOwner(ethClient.credentials.getAddress());
    offerDeal.setTransactionStatus(1);

    OFFER_DEAL_LIST.add(offerDeal);
}

LOG.info(msg, SYMBOL, transactionHash);
```

####  8. Retrieve assets

>Query whether the ETH and ERC20 that have not been retrieved are both 0. If there is still remaining, and the price has passed 25 blocks, then initiate a retrieval transaction.

```java
// The find interface gets the take-order quotation contract that you have not retrieved
List<OfferContractData> offerContractAddresses = getOfferContractAddress();
if (offerContractAddresses.size() == 0) {
    LOG.info("There is currently no quotation contract that needs to be retrieved");
    return;
}

System.out.println("Unrecovered assets：====" + offerContractAddresses.size());
BigInteger gasPrice = null;
try {
    gasPrice = ethClient.ethGasPrice();
} catch (IOException e) {
    LOG.error("An exception occurred when getting gasPrice when retrieving：" + e);
}
gasPrice = gasPrice.multiply(Constant.TURNOUT_GAS_PRICE_MULTIPLE).divide(BigInteger.TEN);
BigInteger nonce = null;
try {
    nonce = ethClient.ethGetTransactionCount();
} catch (IOException e) {
    LOG.error("An exception occurred when getting the nonce when retrieving it：" + e);
}

// Traverse and retrieve all assets
for (OfferContractData contractData : offerContractAddresses) {

    List<Type> typeList = Arrays.<Type>asList(new Address(contractData.getUuid()));
    String transaction = null;
    try {
        transaction = ethClient.turnOut(nonce, typeList, gasPrice);
    } catch (Exception e) {
        LOG.error("Abnormal return price", e.getMessage());
    }
    nonce = nonce.add(BigInteger.ONE);

    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }

    LOG.info("retrieve hash： " + transaction);
}
```

####  9. Exchange trading

>You need to create an Access Key and Secret Key in the Huobi Exchange API management, and fill in the withdrawal address in the withdrawal address management

```java
// Check whether exchange trading is enabled
if (!START_HUOBI_EXCHANGE) {
    return;
}

// Check whether the API-KEY of the exchange is set
if (API_KEY.equalsIgnoreCase("") || API_SECRET.equalsIgnoreCase("")) {
    System.out.println("If the asset needs to be exchanged on the exchange after taking a quote, please set the API-KEY of the exchange and open the deposit and withdrawal permissions");
    return;
}

//When there is no unfinished or pending transaction, initiate a withdrawal operation
if (CollectionUtils.isEmpty(OFFER_DEAL_LIST)) {
    System.out.println("There are no outstanding or pending transactions");
    // At this time, initiate a withdrawal operation
    getToken("eth");
    getToken(EatOfferAndTransactionServiceImpl.SYMBOL.toLowerCase());
    return;
}

// Traverse the data of taking orders and then buy and sell
for (EatOfferDeal offerDeal : OFFER_DEAL_LIST) {
    System.out.println(offerDeal.toString());
    int transactionStatus = offerDeal.getTransactionStatus();

    switch (transactionStatus) {
        // Have a task, recharge
        case 1:
            LOG.info("Receipt of deposit to exchange task");
            recharge(offerDeal);
            break;
        // Recharge has been completed, initiate an order
        case 2:
            LOG.info("Enter the trading");
            if (makeOrder(offerDeal)) return;
            break;
    }
}
```

