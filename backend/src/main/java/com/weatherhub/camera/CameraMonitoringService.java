package com.weatherhub.camera;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weatherhub.camera.dto.CameraMonitoringRecordVO;
import com.weatherhub.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CameraMonitoringService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final CameraSnapshotService snapshotService;
    private final CameraMonitoringRecordMapper recordMapper;
    private final RoboflowMonitoringClient monitoringClient;

    public CameraMonitoringRecord analyzeAndSave(CameraSnapshot snapshot) {
        Map<String, Object> response = monitoringClient.analyze(snapshotService.file(snapshot.getId()));
        Map<String, Object> result = workflowResult(response);
        CameraMonitoringRecord record = new CameraMonitoringRecord();
        record.setCameraId(snapshot.getCameraId());
        record.setSnapshotId(snapshot.getId());
        record.setStatus("SUCCESS");
        record.setWeatherPredictions(json(result.get("weather_predictions")));
        record.setVisibilityPredictions(json(result.get("visibility_predictions")));
        record.setVisibleBoatCount(number(result.get("visible_boat_count")));
        record.setBoatPredictions(json(result.get("boat_predictions")));
        record.setTrackedBoats(json(result.get("tracked_boats")));
        record.setOutputImage(image(result.get("output_image")));
        record.setRawResult(json(response));
        record.setAnalyzedAt(LocalDateTime.now());
        record.setCreatedAt(record.getAnalyzedAt());
        recordMapper.insert(record);
        return record;
    }

    public CameraMonitoringRecord analyzeAndSave(Long snapshotId) {
        return analyzeAndSave(snapshotService.requireSnapshot(snapshotId));
    }

    public Page<CameraMonitoringRecordVO> pageByCamera(Long cameraId, long current, long size) {
        Page<CameraMonitoringRecord> records = recordMapper.selectPage(new Page<>(Math.max(1, current), Math.clamp(size, 1, 100)),
                new LambdaQueryWrapper<CameraMonitoringRecord>().eq(CameraMonitoringRecord::getCameraId, cameraId)
                        .orderByDesc(CameraMonitoringRecord::getAnalyzedAt).orderByDesc(CameraMonitoringRecord::getId));
        Page<CameraMonitoringRecordVO> result = new Page<>(records.getCurrent(), records.getSize(), records.getTotal());
        result.setRecords(records.getRecords().stream().map(record -> CameraMonitoringRecordVO.from(record, false)).toList());
        return result;
    }

    public CameraMonitoringRecordVO get(Long recordId) {
        CameraMonitoringRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new BusinessException(404, "Monitoring record not found");
        return CameraMonitoringRecordVO.from(record, true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> workflowResult(Map<String, Object> response) {
        Object value = response.get("result");
        if (value instanceof List<?> list && !list.isEmpty()) value = list.getFirst();
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new BusinessException(502, "AI monitoring service returned an invalid workflow result");
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return JSON.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException("Unable to serialize AI result", ex); }
    }

    private Integer number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }

    private String image(Object value) {
        if (value instanceof String text) return text;
        if (value instanceof Map<?, ?> map) {
            Object image = map.get("value");
            if (!(image instanceof String)) image = map.get("image");
            if (image instanceof String text) return text;
        }
        return value == null ? null : json(value);
    }
}
