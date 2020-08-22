package com.nest.ib.service.serviceImpl;

import com.nest.ib.constant.Constant;
import com.nest.ib.contract.Nest3OfferMain;
import com.nest.ib.model.OfferContractData;
import com.nest.ib.model.OfferThreeData;
import com.nest.ib.service.EatOfferAndTransactionService;
import com.nest.ib.service.OfferThreeDataService;
import com.nest.ib.utils.EthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Service
public class OfferThreeDataServiceImpl implements OfferThreeDataService {
    private static final Logger LOG = LoggerFactory.getLogger(OfferThreeDataServiceImpl.class);

    @Autowired
    private EatOfferAndTransactionService transactionService;
    @Autowired
    private EthClient ethClient;

    @Override
    public List<OfferThreeData> listOfferData() {

        BigInteger blockNumber = null;
        try {
            blockNumber = ethClient.ethBlockNumber();
        } catch (IOException e) {
            LOG.error("Gets the latest block number exception：{}", e.getMessage());
            e.printStackTrace();
        }

        // Obtain the latest 100 offer contracts
        List<OfferContractData> list = null;
        try {
            list = ethClient.list(Constant.LIST_MAX_COUNT);
        } catch (Exception e) {
            LOG.error("The List interface failed to get contract data：{}", e.getMessage());
            e.printStackTrace();
        }

        if (CollectionUtils.isEmpty(list)) {
            return null;
        }

        List<OfferThreeData> dataList = new ArrayList<>();
        int size = list.size();
        for (int i = 0; i < size; i++) {
            OfferContractData t = list.get(i);
            // Saves the data for the specified token
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

            // Determine if block 25 has passed
            if (blockNumber.subtract(offerThreeData.getBlockNumber()).intValue() >= 25) {
                continue;
            }

            // Determine whether to get back by whether the balance and fee are zero
            BigDecimal ethAmount = new BigDecimal(t.getEthAmount());
            BigDecimal erc20Amount = new BigDecimal(t.getTokenAmount());
            if (ethAmount.compareTo(BigDecimal.ZERO) <= 0
                    && erc20Amount.compareTo(BigDecimal.ZERO) <= 0
                    && offerThreeData.getServiceCharge().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Determine whether the remaining tradable balance is 0
            BigDecimal dealEth = new BigDecimal(t.getDealEthAmount());
            BigDecimal dealErc20 = new BigDecimal(t.getDealTokenAmount());
            if (dealEth.compareTo(BigDecimal.ZERO) <= 0 && dealErc20.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            dataList.add(offerThreeData);
        }
        return dataList;
    }

}
