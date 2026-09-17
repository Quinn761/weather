package com.weatherhub.camera;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weatherhub.camera.dto.CameraAlertVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CameraAlertService {
    private static final String TYPE = "VISIBILITY_LIGHT_HAZE";
    private static final double THRESHOLD = 0.80;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final CameraAlertMapper alertMapper;
    private final CameraDeviceMapper cameraDeviceMapper;

    public void evaluate(CameraMonitoringRecord record) {
        Detection detection = detection(record.getVisibilityPredictions());
        CameraAlert active = active(record.getCameraId());
        if (detection.matches()) {
            LocalDateTime now = record.getAnalyzedAt();
            if (active == null) create(record, detection, now);
            else {
                active.setMonitoringRecordId(record.getId());
                active.setConfidence(detection.confidence());
                active.setNormalCount(0);
                active.setLastDetectedAt(now);
                active.setUpdatedAt(now);
                alertMapper.updateById(active);
            }
        } else if (active != null) {
            int normalCount = (active.getNormalCount() == null ? 0 : active.getNormalCount()) + 1;
            active.setNormalCount(normalCount);
            active.setUpdatedAt(record.getAnalyzedAt());
            if (normalCount >= 2) {
                active.setStatus("RECOVERED");
                active.setRecoveredAt(record.getAnalyzedAt());
            }
            alertMapper.updateById(active);
        }
    }

    public List<CameraAlertVO> activeAlerts() {
        return alertMapper.selectList(new LambdaQueryWrapper<CameraAlert>()
                        .eq(CameraAlert::getStatus, "ACTIVE").orderByDesc(CameraAlert::getLastDetectedAt))
                .stream().map(CameraAlertVO::from).toList();
    }

    public List<CameraAlertVO> byCamera(Long cameraId) {
        return alertMapper.selectList(new LambdaQueryWrapper<CameraAlert>().eq(CameraAlert::getCameraId, cameraId)
                        .orderByDesc(CameraAlert::getLastDetectedAt).last("LIMIT 50"))
                .stream().map(CameraAlertVO::from).toList();
    }

    private CameraAlert active(Long cameraId) {
        return alertMapper.selectOne(new LambdaQueryWrapper<CameraAlert>().eq(CameraAlert::getCameraId, cameraId)
                .eq(CameraAlert::getType, TYPE).eq(CameraAlert::getStatus, "ACTIVE").last("LIMIT 1"));
    }

    private void create(CameraMonitoringRecord record, Detection detection, LocalDateTime now) {
        CameraDevice camera = cameraDeviceMapper.selectById(record.getCameraId());
        String name = camera == null ? "摄像头" : camera.getName();
        CameraAlert alert = new CameraAlert();
        alert.setCameraId(record.getCameraId()); alert.setMonitoringRecordId(record.getId());
        alert.setType(TYPE); alert.setLevel("YELLOW"); alert.setStatus("ACTIVE");
        alert.setTitle("能见度异常 · 轻度雾霾");
        alert.setContent(String.format("%s 识别到轻度雾霾，置信度 %.0f%%。请关注道路通行与现场能见度。", name, detection.confidence() * 100));
        alert.setConfidence(detection.confidence()); alert.setNormalCount(0); alert.setFirstDetectedAt(now); alert.setLastDetectedAt(now);
        alert.setCreatedAt(now); alert.setUpdatedAt(now); alertMapper.insert(alert);
    }

    private Detection detection(String raw) {
        try {
            JsonNode root = JSON.readTree(raw == null ? "{}" : raw);
            String top = root.path("top").asString("");
            double confidence = root.path("confidence").asDouble(0);
            if ("轻度雾霾".equals(top)) return new Detection(confidence >= THRESHOLD, confidence);
            JsonNode predictions = root.get("predictions");
            if (predictions != null && predictions.isArray()) for (JsonNode item : predictions) {
                if ("轻度雾霾".equals(item.path("class").asString(""))) {
                    double value = item.path("confidence").asDouble(0);
                    return new Detection(value >= THRESHOLD, value);
                }
            }
        } catch (Exception ignored) { }
        return new Detection(false, 0);
    }

    private record Detection(boolean matches, double confidence) { }
}
