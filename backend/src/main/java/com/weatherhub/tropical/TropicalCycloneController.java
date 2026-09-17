package com.weatherhub.tropical;

import com.weatherhub.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tropical-cyclones")
@RequiredArgsConstructor
public class TropicalCycloneController {
    private final TropicalCycloneService tropicalCycloneService;

    @GetMapping
    public ApiResponse<TropicalCycloneService.TropicalOverview> overview() {
        return ApiResponse.ok(tropicalCycloneService.overview());
    }
}
