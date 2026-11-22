package com.llijat.t212visualizer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "custom_categories")
public class CustomCategory {
    @Id
    private String name;
    private Instant createdAt;

    protected CustomCategory() {}

    public CustomCategory(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public String getName() {return name;}
    public Instant getCreatedAt() {return createdAt;}
}
