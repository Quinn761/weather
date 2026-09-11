package com.weatherhub.gis;

import com.weatherhub.common.BusinessException;
import com.weatherhub.gis.dto.GisFeatureVO;
import com.weatherhub.gis.dto.SaveGisFeatureRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "weatherhub.gis.enabled", havingValue = "true", matchIfMissing = true)
public class GisFeatureService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter LAND_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final GisFeatureMapper gisFeatureMapper;

    public List<GisFeatureVO> list() {
        return gisFeatureMapper.selectAllFeatures().stream()
                .filter(feature -> "POLYGON".equalsIgnoreCase(feature.getType()) || "SURFACE".equalsIgnoreCase(feature.getType()))
                .map(GisFeatureVO::from)
                .toList();
    }

    @Transactional("gisTransactionManager")
    public GisFeatureVO create(SaveGisFeatureRequest request) {
        String type = request.type().trim().toUpperCase();
        if (!"POLYGON".equals(type) && !"SURFACE".equals(type)) {
            throw new BusinessException("只支持圈地地块标注");
        }
        GisFeature feature = new GisFeature();
        feature.setName(generateLandName());
        feature.setType(type);
        feature.setGeometryJson(extractGeometryJson(request.geojson()));
        feature.setProperties(StringUtils.hasText(request.properties()) ? request.properties().trim() : "{}");
        feature.setGeojson(request.geojson().trim());
        gisFeatureMapper.insertFeature(feature);
        return GisFeatureVO.from(feature);
    }

    private String generateLandName() {
        return "地块-" + LocalDateTime.now().format(LAND_NAME_FORMATTER);
    }

    @Transactional("gisTransactionManager")
    public void delete(Long id) {
        if (gisFeatureMapper.deleteById(id) == 0) {
            throw new BusinessException(404, "标注不存在");
        }
    }

    private String extractGeometryJson(String geojson) {
        if (!StringUtils.hasText(geojson)) {
            throw new BusinessException("GeoJSON 不能为空");
        }
        try {
            JsonNode root = JSON.readTree(geojson);
            if (root == null || root.isNull()) {
                throw new BusinessException("GeoJSON 不能为空");
            }
            String typeName = text(root.get("type"));
            if ("Feature".equals(typeName)) {
                JsonNode geometry = root.get("geometry");
                if (geometry == null || geometry.isNull()) {
                    throw new BusinessException("GeoJSON 缺少 geometry");
                }
                return JSON.writeValueAsString(geometry);
            }
            if (root.get("coordinates") != null && !root.get("coordinates").isNull()) {
                return geojson.trim();
            }
            throw new BusinessException("不支持的 GeoJSON 类型");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("GeoJSON 无效");
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asString();
    }
}
