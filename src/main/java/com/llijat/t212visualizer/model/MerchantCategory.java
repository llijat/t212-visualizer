package com.llijat.t212visualizer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "merchant_categories")
public class MerchantCategory {
    @Id
    private String merchantName;
    private String category;

    protected MerchantCategory() {}

    public MerchantCategory(String merchantName) {
        this.merchantName = merchantName;
    }

    public void changeCategory(String category) {
        this.category = category;
    }

    public String getCategory() {return category;}
    public String getMerchantName() {return merchantName;}

}
