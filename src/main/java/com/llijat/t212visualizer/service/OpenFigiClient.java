package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.OpenFigiListingDto;

import java.util.List;
import java.util.Map;

public interface OpenFigiClient {
    Map<String, List<OpenFigiListingDto>> findListings(List<String> isins);
}
