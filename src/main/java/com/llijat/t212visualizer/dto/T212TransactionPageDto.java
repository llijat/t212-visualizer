package com.llijat.t212visualizer.dto;

import java.util.List;

public record T212TransactionPageDto (
        List<T212TransactionDto> items,
        String nextPagePath
){}
