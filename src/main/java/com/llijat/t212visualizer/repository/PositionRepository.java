package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, String> {
}
