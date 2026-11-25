package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.OpenFigiListingDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Profile("demo")
public class DemoOpenFigiClient implements OpenFigiClient{

    @Override
    public Map<String, List<OpenFigiListingDto>> findListings(List<String> isins) {
        return Map.of();
    }
}
