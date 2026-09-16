package com.weatherhub.camera;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weatherhub.camera.dto.CameraSnapshotVO;
import com.weatherhub.common.BusinessException;
import com.weatherhub.config.CameraCaptureProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CameraSnapshotService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final CameraSnapshotMapper snapshotMapper;
    private final CameraCaptureProperties properties;

    public Page<CameraSnapshotVO> pageByCamera(Long cameraId, long current, long size) {
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.clamp(size, 1, 100);
        Page<CameraSnapshot> snapshots = snapshotMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<CameraSnapshot>()
                        .eq(CameraSnapshot::getCameraId, cameraId)
                        .orderByDesc(CameraSnapshot::getCapturedAt)
                        .orderByDesc(CameraSnapshot::getId));
        Page<CameraSnapshotVO> result = new Page<>(snapshots.getCurrent(), snapshots.getSize(), snapshots.getTotal());
        result.setRecords(snapshots.getRecords().stream()
                .map(snapshot -> CameraSnapshotVO.from(snapshot, imageDataUrl(snapshot))).toList());
        return result;
    }

    public Resource image(Long snapshotId) {
        return new FileSystemResource(file(snapshotId));
    }

    Path file(Long snapshotId) {
        CameraSnapshot snapshot = requireSnapshot(snapshotId);
        Path file = storageRoot().resolve(snapshot.getStoragePath()).normalize();
        if (!file.startsWith(storageRoot()) || !Files.isRegularFile(file)) {
            throw new BusinessException(404, "抓拍图片不存在");
        }
        return file;
    }

    private String imageDataUrl(CameraSnapshot snapshot) {
        Path image = storageRoot().resolve(snapshot.getStoragePath()).normalize();
        if (!image.startsWith(storageRoot()) || !Files.isRegularFile(image)) {
            return null;
        }
        try {
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(thumbnail(image));
        } catch (IOException ex) {
            return null;
        }
    }

    /** A small embedded preview keeps one paginated request fast without a browser request per image. */
    private byte[] thumbnail(Path image) throws IOException {
        BufferedImage source = ImageIO.read(image.toFile());
        if (source == null) return Files.readAllBytes(image);
        double scale = Math.min(1d, Math.min(480d / source.getWidth(), 270d / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = preview.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(0.68f);
            writer.write(null, new IIOImage(preview, null, null), parameters);
            writer.dispose();
            return bytes.toByteArray();
        }
    }

    public CameraSnapshot capture(CameraDevice camera) throws IOException, InterruptedException {
        if (!StringUtils.hasText(properties.streamUrl())) {
            throw new IllegalStateException("未配置 CAMERA_CAPTURE_STREAM_URL");
        }
        LocalDateTime now = LocalDateTime.now();
        String relativePath = camera.getId() + "/" + now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/" + FILE_TIME.format(now) + ".jpg";
        Path output = storageRoot().resolve(relativePath).normalize();
        if (!output.startsWith(storageRoot())) throw new IllegalStateException("非法抓拍存储路径");
        Files.createDirectories(output.getParent());

        Process process = new ProcessBuilder(
                properties.ffmpegCommand(), "-y", "-loglevel", "error",
                "-i", properties.streamUrl(), "-frames:v", "1", "-update", "1", "-q:v", "2", output.toString()
        ).redirectErrorStream(true).start();
        boolean complete = process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS);
        if (!complete) {
            process.destroyForcibly();
            Files.deleteIfExists(output);
            throw new IllegalStateException("FFmpeg 抓拍超时");
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
            Files.deleteIfExists(output);
            throw new IllegalStateException("FFmpeg 未生成有效抓拍图片，退出码：" + process.exitValue());
        }

        CameraSnapshot snapshot = new CameraSnapshot();
        snapshot.setCameraId(camera.getId());
        snapshot.setStoragePath(relativePath.replace('\\', '/'));
        snapshot.setContentType("image/jpeg");
        snapshot.setFileSize(Files.size(output));
        snapshot.setCapturedAt(now);
        snapshot.setCreatedAt(now);
        snapshotMapper.insert(snapshot);
        return snapshot;
    }

    CameraSnapshot requireSnapshot(Long id) {
        CameraSnapshot snapshot = snapshotMapper.selectById(id);
        if (snapshot == null) throw new BusinessException(404, "抓拍记录不存在");
        return snapshot;
    }

    private Path storageRoot() {
        return Path.of(properties.storagePath()).toAbsolutePath().normalize();
    }
}
