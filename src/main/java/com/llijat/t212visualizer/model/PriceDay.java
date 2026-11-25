package com.llijat.t212visualizer.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
public record PriceDay(
        String symbol,
        LocalDate day
) implements Serializable {}