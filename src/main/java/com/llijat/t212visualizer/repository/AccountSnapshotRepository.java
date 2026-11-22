package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.AccountSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshot, LocalDate> {
    Optional<AccountSnapshot> findFirstBySnapshotDateBeforeOrderBySnapshotDateDesc(LocalDate date);
}
