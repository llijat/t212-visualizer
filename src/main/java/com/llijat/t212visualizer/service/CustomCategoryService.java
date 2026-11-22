package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CategoryNames;
import com.llijat.t212visualizer.model.CustomCategory;
import com.llijat.t212visualizer.repository.BudgetRepository;
import com.llijat.t212visualizer.repository.CustomCategoryRepository;
import com.llijat.t212visualizer.repository.MerchantCategoryRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomCategoryService {
    private static final List<String> T212_CATEGORIES = List.of("AUTOMOBILE", "AUTOMOBILE_RENTAL", "BUSINESS_SERVICES", "CLOTHING_STORES", "ENTERTAINMENT", "GOVERNMENT_SERVICES", "HOTELS", "OTHER", "PERSONAL_SERVICES", "RESTAURANTS", "RETAIL_STORES", "SERVICE_PROVIDERS", "TRANSPORT");

    private final CustomCategoryRepository customCategoryRepository;
    private final MerchantCategoryRepository merchantCategoryRepository;
    private final BudgetRepository budgetRepository;


    public CustomCategoryService(CustomCategoryRepository customCategoryRepository, MerchantCategoryRepository merchantCategoryRepository, BudgetRepository budgetRepository) {
        this.customCategoryRepository = customCategoryRepository;
        this.merchantCategoryRepository = merchantCategoryRepository;
        this.budgetRepository = budgetRepository;
    }

    public List<CustomCategory> getAll() {
        return customCategoryRepository.findAllByOrderByCreatedAtAsc();
    }

    public CategoryNames getCategoryNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (String category : T212_CATEGORIES) names.put(category, CategoryNames.fromKey(category));
        for (CustomCategory custom : getAll()) names.put(custom.getName(), custom.getName());
        return new CategoryNames(names);
    }

    /** Creates it and gives back the name as it was saved, trimmed. */
    public String create(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Write a name for the category.");
        String trimmed = name.trim();
        if (trimmed.length() > 100) throw new IllegalArgumentException("The name can be 100 characters at most.");
        if (customCategoryRepository.existsById(trimmed)) throw new IllegalArgumentException("That category already exists.");
        if (T212_CATEGORIES.contains(trimmed)) throw new IllegalArgumentException("That name is already a Trading 212 category.");

        customCategoryRepository.save(new CustomCategory(trimmed));
        return trimmed;
    }


    /** Its merchants go back to the T212 category, and its budget goes away: all of it or nothing. */
    @Transactional
    public void delete(String name) {
        merchantCategoryRepository.deleteByCategory(name);
        budgetRepository.deleteById(name);
        customCategoryRepository.deleteById(name);
    }
}