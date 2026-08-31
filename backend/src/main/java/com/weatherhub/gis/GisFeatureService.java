package com.weatherhub.gis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.weatherhub.common.BusinessException;
import com.weatherhub.gis.dto.GisFeatureVO;
import com.weatherhub.gis.dto.SaveGisFeatureRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GisFeatureService {
    private final GisFeatureMapper gisFeatureMapper;

    public List<GisFeatureVO> list() {
        return gisFeatureMapper.selectList(new LambdaQueryWrapper<GisFeature>()
                        .orderByDesc(GisFeature::getCreatedAt)
                        .orderByDesc(GisFeature::getId))
                .stream()
                .map(GisFeatureVO::from)
                .toList();
    }

    @Transactional
    public GisFeatureVO create(SaveGisFeatureRequest request) {
        String type = request.type().trim().toUpperCase();
        if (!"POINT".equals(type) && !"POLYGON".equals(type)) {
            throw new BusinessException("不支持的标注类型");
        }
        GisFeature feature = new GisFeature();
        feature.setName(request.name().trim());
        feature.setType(type);
        feature.setGeojson(request.geojson().trim());
        feature.setProperties(StringUtils.isBlank(request.properties()) ? "{}" : request.properties().trim());
        gisFeatureMapper.insert(feature);
        return GisFeatureVO.from(feature);
    }

    @Transactional
    public void delete(Long id) {
        if (gisFeatureMapper.deleteById(id) == 0) {
            throw new BusinessException(404, "标注不存在");
        }
    }
}
