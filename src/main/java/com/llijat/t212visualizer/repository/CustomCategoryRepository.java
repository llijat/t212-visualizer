package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.CustomCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomCategoryRepository extends JpaRepository<CustomCategory, String> {
    List<CustomCategory> findAllByOrderByCreatedAtAsc();
}