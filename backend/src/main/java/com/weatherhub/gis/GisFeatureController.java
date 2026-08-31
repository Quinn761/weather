package com.weatherhub.gis;

import com.weatherhub.common.ApiResponse;
import com.weatherhub.gis.dto.GisFeatureVO;
import com.weatherhub.gis.dto.SaveGisFeatureRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gis/features")
@RequiredArgsConstructor
public class GisFeatureController {
    private final GisFeatureService gisFeatureService;

    @GetMapping
    public ApiResponse<List<GisFeatureVO>> list() {
        return ApiResponse.ok(gisFeatureService.list());
    }

    @PostMapping
    public ApiResponse<GisFeatureVO> create(@Valid @RequestBody SaveGisFeatureRequest request) {
        return ApiResponse.ok(gisFeatureService.create(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        gisFeatureService.delete(id);
        return ApiResponse.ok();
    }
}
