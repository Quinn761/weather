package com.weatherhub.officialalert;

import com.weatherhub.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/official-alerts")
@RequiredArgsConstructor
public class OfficialAlertController {
    private final OfficialAlertService officialAlertService;

    @GetMapping
    public ApiResponse<OfficialAlertService.OfficialAlertOverview> overview() {
        return ApiResponse.ok(officialAlertService.overview());
    }
}
