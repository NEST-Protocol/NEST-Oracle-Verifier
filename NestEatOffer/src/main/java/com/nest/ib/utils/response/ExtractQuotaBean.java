package com.nest.ib.utils.response;

import java.io.Serializable;

/**
 * ClassName:ExtractQuotaBean
 * Description:
 */
public class ExtractQuotaBean<T> implements Serializable {
    private String currency;
    private T chains;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public T getChains() {
        return chains;
    }

    public void setChains(T chains) {
        this.chains = chains;
    }
}
