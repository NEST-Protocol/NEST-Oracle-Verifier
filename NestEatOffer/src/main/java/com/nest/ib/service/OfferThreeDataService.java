package com.nest.ib.service;


import com.nest.ib.model.OfferContractData;
import com.nest.ib.model.OfferThreeData;

import java.util.List;

public interface OfferThreeDataService {

    /**
     * Obtain contract order information for verification
     * @return
     */
    List<OfferThreeData> listOfferData();
}
