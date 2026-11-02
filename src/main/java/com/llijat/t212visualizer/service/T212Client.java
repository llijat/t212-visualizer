package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.dto.T212PositionDto;
import com.llijat.t212visualizer.dto.T212TransactionPageDto;

import java.util.List;

public interface T212Client {
    List<T212PositionDto> fetchPositions();
    T212AccountSummaryDto fetchAccountSummary();
    T212TransactionPageDto fetchTransactions(String path);
}
