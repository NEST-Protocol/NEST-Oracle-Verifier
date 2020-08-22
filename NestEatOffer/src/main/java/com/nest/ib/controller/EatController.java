package com.nest.ib.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nest.ib.service.EatOfferAndTransactionService;
import com.nest.ib.service.serviceImpl.EatOfferAndTransactionServiceImpl;
import com.nest.ib.utils.EthClient;
import com.nest.ib.utils.HttpClientUtil;
import com.nest.ib.utils.api.ApiClient;
import com.nest.ib.utils.api.JsonUtil;
import com.nest.ib.utils.response.Accounts;
import com.nest.ib.utils.response.AccountsResponse;
import com.nest.ib.utils.response.BalanceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author wll
 * @date 2020/7/26 14:52
 */
@RequestMapping("eat")
@RestController
public class EatController {

    @Autowired
    EthClient ethClient;

    @Autowired
    EatOfferAndTransactionService eatOfferAndTransactionService;

    /**
     * Set the node
     */
    @PostMapping("/updateNode")
    public String updateNode(@RequestParam(name = "node") String node) {
        ethClient.initNode(node);

        // If the key is already filled in, the node is reset, and a new registration is required
        ethClient.resetBean();
        return node;
    }

    /**
     * Set token addresses and transaction pairs
     */
    @PostMapping("/updateErc20")
    public String updateErc20(@RequestParam(name = "erc20Addr") String erc20Addr, @RequestParam(name = "symbols") String symbols) {
        EatOfferAndTransactionServiceImpl.ERC20_TOKEN_ADDRESS = erc20Addr;
        EatOfferAndTransactionServiceImpl.SYMBOLS = symbols.toLowerCase();

        // If the key has been filled in, the address and transaction pairs that have been reset need to be re-registered
        ethClient.resetBean();
        return "ok";
    }

    /**
     * Set the order ratio
     */
    @PostMapping("/updateEatRate")
    public BigDecimal updateEatRate(@RequestParam(name = "eatRate") BigDecimal eatRate) {
        EatOfferAndTransactionServiceImpl.EAT_PRICE_OFFERSET = eatRate;
        return eatRate;
    }

    /**
     * On/off eating menu. True on,false off
     */
    @PostMapping("/startEat")
    public boolean startEat() {
        EatOfferAndTransactionServiceImpl.START_EAT = !EatOfferAndTransactionServiceImpl.START_EAT;
        return EatOfferAndTransactionServiceImpl.START_EAT;
    }

    /**
     * Close/open the Huocoin Exchange (to buy and sell the assets acquired after the order)
     */
    @PostMapping("/updateHuobiExchangeState")
    public boolean updateHuobiExchangeState() {
        return eatOfferAndTransactionService.updateHuobiExchange();
    }

    /**
     * Sets whether user authentication is enabled
     */
    @PostMapping("/updateAuthorizedUser")
    public void updateAuthorizedUser() {
        eatOfferAndTransactionService.updateAuthorizedUser();
    }

    /**
     * Set the API-Key and API-Secret of huocoin exchange
     *
     * @return
     */
    @PostMapping("/updateExchangeApiKey")
    public List updateExchangeApiKey(@RequestParam(name = "apiKey") String apiKey, @RequestParam(name = "apiSecret") String apiSecret) {
        ApiClient client = new ApiClient(apiKey, apiSecret);
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
            List balanceList = new ArrayList<>();

            list.forEach(li -> {
                JSONObject jsonObject2 = JSONObject.parseObject(String.valueOf(li));
                String balanceStr = jsonObject2.getString("balance");
                String currency = jsonObject2.getString("currency");

                // Find the balance of HT,ETH,USDT
                if (currency.equalsIgnoreCase(EatOfferAndTransactionServiceImpl.SYMBOL)
                        || currency.equalsIgnoreCase("ETH")) {
                    HashMap hashMap = new HashMap();
                    hashMap.put("balance", balanceStr);
                    hashMap.put("currency", currency);
                    balanceList.add(hashMap);
                }
            });

            String result = eatOfferAndTransactionService.updateExchangeApiKey(apiKey, apiSecret);
            if (result.equalsIgnoreCase("SUCCESS")) {
                return balanceList;
            }
        }
        return null;
    }

    /**
     * Set the account private key
     */
    @PostMapping("/updatePrivateKey")
    public String updateUserPrivatekey(@RequestParam(name = "privateKey") String privateKey) {
        ethClient.updateUserPrivateKey(privateKey);
        return ethClient.credentials.getAddress();
    }

    /**
     * Set the network agent address and port
     * @param proxyIp
     * @param proxyPort
     * @return
     */
    @PostMapping("/updateProxy")
    public String updateProxy(@RequestParam(name = "proxyIp") String proxyIp, @RequestParam(name = "proxyPort") int proxyPort) {
        HttpClientUtil.updateProxy(proxyIp, proxyPort);
        return "ok";
    }

    /**
     * Configuration page
     *
     * @return
     */
    @GetMapping("/eatData")
    public ModelAndView miningData() {
        String address = ethClient.getAddress();
        boolean b = EatOfferAndTransactionServiceImpl.START_EAT;
        Map<String, String> data = eatOfferAndTransactionService.eatOfferData();

        ModelAndView mav = new ModelAndView("eatData");
        mav.addObject("address", address);
        mav.addObject("startEat", b == true ? "Eat the arbitrage state: open" : "Eat the arbitrage state: close");
        mav.addObject("apiKey", data.get("apiKey"));
        mav.addObject("apiSecret", data.get("apiSecret"));
        mav.addObject("authorizedUser", data.get("authorizedUser").equalsIgnoreCase("true") ? "Exchange certification：open" : "Exchange certification：close");
        mav.addObject("huobiExchangeState", data.get("huobiExchangeState").equalsIgnoreCase("true") ? "Exchange state：Open business" : "Exchange state：Close business");
        // Convert to percentage
        BigDecimal eatRate = EatOfferAndTransactionServiceImpl.EAT_PRICE_OFFERSET.multiply(BigDecimal.valueOf(100L));
        mav.addObject("eatRate", eatRate + "%");
        mav.addObject("node", EthClient.NODE);
        mav.addObject("erc20Addr", EatOfferAndTransactionServiceImpl.ERC20_TOKEN_ADDRESS);
        mav.addObject("symbols", EatOfferAndTransactionServiceImpl.SYMBOLS);
        mav.addObject("proxyIp", HttpClientUtil.getProxyIp());
        mav.addObject("proxyPort", HttpClientUtil.getProxyPort());

        return mav;
    }
}
