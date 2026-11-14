package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, String> {
}