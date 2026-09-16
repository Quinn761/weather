package com.weatherhub.camera;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weatherhub.camera.dto.CameraDeviceVO;
import com.weatherhub.camera.dto.SaveCameraDeviceRequest;
import com.weatherhub.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CameraDeviceService {
    private static final String HIKVISION = "海康";
    private final CameraDeviceMapper cameraDeviceMapper;
    private final CameraCredentialCodec credentialCodec;

    public List<CameraDeviceVO> list() {
        return cameraDeviceMapper.selectList(new LambdaQueryWrapper<CameraDevice>()
                        .orderByDesc(CameraDevice::getUpdatedAt)
                        .orderByDesc(CameraDevice::getId))
                .stream().map(CameraDeviceVO::from).toList();
    }

    @Transactional
    public CameraDeviceVO create(SaveCameraDeviceRequest request) {
        String verificationCode = requiredVerificationCode(request.verificationCode());
        CameraDevice device = new CameraDevice();
        apply(device, request);
        device.setVerificationCode(credentialCodec.encrypt(verificationCode));
        LocalDateTime now = LocalDateTime.now();
        device.setCreatedAt(now);
        device.setUpdatedAt(now);
        ensureUniqueSerial(device.getSerialNumber(), null);
        cameraDeviceMapper.insert(device);
        return CameraDeviceVO.from(device);
    }

    @Transactional
    public CameraDeviceVO update(Long id, SaveCameraDeviceRequest request) {
        CameraDevice device = require(id);
        apply(device, request);
        if (StringUtils.hasText(request.verificationCode())) {
            device.setVerificationCode(credentialCodec.encrypt(request.verificationCode().trim()));
        }
        device.setUpdatedAt(LocalDateTime.now());
        ensureUniqueSerial(device.getSerialNumber(), id);
        cameraDeviceMapper.updateById(device);
        return CameraDeviceVO.from(device);
    }

    @Transactional
    public void delete(Long id) {
        if (cameraDeviceMapper.deleteById(id) == 0) {
            throw new BusinessException(404, "摄像头不存在");
        }
    }

    private void apply(CameraDevice device, SaveCameraDeviceRequest request) {
        validateCoordinates(request.longitude(), request.latitude());
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase() : "ONLINE";
        if (!"ONLINE".equals(status) && !"OFFLINE".equals(status)) {
            throw new BusinessException("摄像头状态只能为 ONLINE 或 OFFLINE");
        }
        device.setName(request.name().trim());
        device.setBrand(HIKVISION);
        device.setSerialNumber(request.serialNumber().trim());
        device.setLongitude(request.longitude());
        device.setLatitude(request.latitude());
        device.setStatus(status);
    }

    private String requiredVerificationCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("新增摄像头必须填写验证码");
        }
        return value.trim();
    }

    private void validateCoordinates(Double longitude, Double latitude) {
        if ((longitude == null) != (latitude == null)) {
            throw new BusinessException("经度和纬度必须同时填写");
        }
        if (longitude != null && (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90)) {
            throw new BusinessException("摄像头坐标超出有效范围");
        }
    }

    private CameraDevice require(Long id) {
        CameraDevice device = cameraDeviceMapper.selectById(id);
        if (device == null) throw new BusinessException(404, "摄像头不存在");
        return device;
    }

    private void ensureUniqueSerial(String serialNumber, Long excludeId) {
        Long count = cameraDeviceMapper.selectCount(new LambdaQueryWrapper<CameraDevice>()
                .eq(CameraDevice::getSerialNumber, serialNumber)
                .ne(excludeId != null, CameraDevice::getId, excludeId));
        if (count != null && count > 0) throw new BusinessException("设备序列号已存在");
    }
}
