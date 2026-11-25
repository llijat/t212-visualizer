package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.MerchantCategory;
import com.llijat.t212visualizer.repository.MerchantCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MerchantCategoryService {
    private final MerchantCategoryRepository merchantCategoryRepository;

    public MerchantCategoryService(MerchantCategoryRepository merchantCategoryRepository) { this.merchantCategoryRepository = merchantCategoryRepository;}

    public void saveCategory(String merchantName, String category) {
        if (merchantName == null || merchantName.isBlank()) throw new IllegalArgumentException("Choose a merchant.");
        if (category == null || category.isBlank() || category.length() > 255) throw new IllegalArgumentException("Choose a category.");


        MerchantCategory correction = merchantCategoryRepository.findById(merchantName).orElseGet(() -> new MerchantCategory(merchantName));
        correction.changeCategory(category);
        merchantCategoryRepository.save(correction);
    }

    public void resetCategory(String merchantName) {
        merchantCategoryRepository.deleteById(merchantName);
    }

    public Map<String,String> getCorrections() {
        return merchantCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MerchantCategory::getMerchantName,MerchantCategory::getCategory));
    }
}
