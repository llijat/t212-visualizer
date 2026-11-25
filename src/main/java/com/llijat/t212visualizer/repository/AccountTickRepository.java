package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.AccountTick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AccountTickRepository extends JpaRepository<AccountTick, Instant> {
}
