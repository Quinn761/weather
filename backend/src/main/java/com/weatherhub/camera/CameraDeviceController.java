package com.weatherhub.camera;

import com.weatherhub.camera.dto.CameraDeviceVO;
import com.weatherhub.camera.dto.CameraSnapshotVO;
import com.weatherhub.camera.dto.CameraMonitoringRecordVO;
import com.weatherhub.camera.dto.CameraAlertVO;
import com.weatherhub.camera.dto.SaveCameraDeviceRequest;
import com.weatherhub.common.ApiResponse;
import com.weatherhub.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraDeviceController {
    private final CameraDeviceService cameraDeviceService;
    private final CameraSnapshotService cameraSnapshotService;
    private final CameraMonitoringService cameraMonitoringService;
    private final CameraAlertService cameraAlertService;
    private final RoboflowMonitoringClient roboflowMonitoringClient;

    @GetMapping
    public ApiResponse<List<CameraDeviceVO>> list() { return ApiResponse.ok(cameraDeviceService.list()); }

    @GetMapping("/{id}/snapshots")
    public ApiResponse<PageResult<CameraSnapshotVO>> snapshots(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(PageResult.of(cameraSnapshotService.pageByCamera(id, current, size)));
    }

    @GetMapping("/snapshots/{snapshotId}/image")
    public ResponseEntity<Resource> snapshotImage(@PathVariable Long snapshotId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(cameraSnapshotService.image(snapshotId));
    }

    @PostMapping("/snapshots/{snapshotId}/monitoring")
    public ApiResponse<Map<String, Object>> monitorSnapshot(@PathVariable Long snapshotId) {
        CameraMonitoringRecord record = cameraMonitoringService.analyzeAndSave(snapshotId);
        return ApiResponse.ok(Map.of("recordId", record.getId(), "status", record.getStatus()));
    }

    @GetMapping("/{id}/monitoring-records")
    public ApiResponse<PageResult<CameraMonitoringRecordVO>> monitoringRecords(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(PageResult.of(cameraMonitoringService.pageByCamera(id, current, size)));
    }

    @GetMapping("/monitoring-records/{recordId}")
    public ApiResponse<CameraMonitoringRecordVO> monitoringRecord(@PathVariable Long recordId) {
        return ApiResponse.ok(cameraMonitoringService.get(recordId));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<CameraAlertVO>> activeAlerts() {
        return ApiResponse.ok(cameraAlertService.activeAlerts());
    }

    @GetMapping("/{id}/alerts")
    public ApiResponse<List<CameraAlertVO>> cameraAlerts(@PathVariable Long id) {
        return ApiResponse.ok(cameraAlertService.byCamera(id));
    }

    @PostMapping
    public ApiResponse<CameraDeviceVO> create(@Valid @RequestBody SaveCameraDeviceRequest request) {
        return ApiResponse.ok(cameraDeviceService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CameraDeviceVO> update(@PathVariable Long id, @Valid @RequestBody SaveCameraDeviceRequest request) {
        return ApiResponse.ok(cameraDeviceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        cameraDeviceService.delete(id);
        return ApiResponse.ok();
    }
}
