"""Optional SAM 2.1 inference; heavy dependencies are loaded only on demand."""
import base64
import binascii
import io
import logging
import math
import os
from pathlib import Path
from threading import Lock

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field, model_validator

router = APIRouter()
_lock = Lock()  # SAM2ImagePredictor stores per-image state; serialize its use.
_predictor = None
logger = logging.getLogger(__name__)


class Bounds(BaseModel):
    west: float = Field(ge=-180, le=180, allow_inf_nan=False)
    east: float = Field(ge=-180, le=180, allow_inf_nan=False)
    south: float = Field(ge=-85.051129, le=85.051129, allow_inf_nan=False)
    north: float = Field(ge=-85.051129, le=85.051129, allow_inf_nan=False)

    @model_validator(mode="after")
    def ordered(self):
        if self.west >= self.east or self.south >= self.north:
            raise ValueError("影像地理范围无效")
        return self


class SegmentRequest(BaseModel):
    imageBase64: str = Field(min_length=1, max_length=8_000_000)
    bounds: Bounds
    longitude: float = Field(allow_inf_nan=False)
    latitude: float = Field(allow_inf_nan=False)

    @model_validator(mode="after")
    def click_in_bounds(self):
        b = self.bounds
        if not (b.west <= self.longitude <= b.east and b.south <= self.latitude <= b.north):
            raise ValueError("点击位置必须位于影像范围内")
        return self


def mercator_y(latitude):
    return math.asinh(math.tan(math.radians(latitude)))


def click_pixel(request, width, height):
    b = request.bounds
    x = (request.longitude - b.west) / (b.east - b.west) * width
    y = (mercator_y(b.north) - mercator_y(request.latitude)) / (
        mercator_y(b.north) - mercator_y(b.south)) * height
    return min(width - 1, max(0, int(x))), min(height - 1, max(0, int(y)))


def pixel_lonlat(x, y, bounds, width, height):
    # Contours pass through pixel centers in a Web Mercator raster.
    lon = bounds.west + ((x + 0.5) / width) * (bounds.east - bounds.west)
    north, south = mercator_y(bounds.north), mercator_y(bounds.south)
    lat = math.degrees(math.atan(math.sinh(north - ((y + 0.5) / height) * (north - south))))
    return [round(lon, 8), round(lat, 8)]


def get_predictor():
    global _predictor
    if _predictor is not None:
        return _predictor
    checkpoint = os.getenv("SAM2_CHECKPOINT", "").strip()
    if not checkpoint or not Path(checkpoint).is_file():
        raise HTTPException(503, "SAM 2.1 尚未配置：请安装分割依赖并设置 SAM2_CHECKPOINT 权重路径")
    from sam2.build_sam import build_sam2
    from sam2.sam2_image_predictor import SAM2ImagePredictor
    import torch
    device = os.getenv("SAM2_DEVICE", "auto")
    if device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    config = os.getenv("SAM2_CONFIG", "configs/sam2.1/sam2.1_hiera_t.yaml")
    _predictor = SAM2ImagePredictor(build_sam2(config, checkpoint, device=device))
    return _predictor


def mask_feature(masks, scores, point, bounds):
    import cv2
    import numpy as np
    x, y = point
    # Select highest-quality mask containing the prompt, then only its connected component.
    for index in np.argsort(scores)[::-1]:
        if not np.isfinite(scores[index]):
            continue
        mask = np.asarray(masks[index], dtype=np.uint8)
        if not mask[y, x]:
            continue
        _, labels = cv2.connectedComponents(mask, connectivity=8)
        component = np.asarray(labels == labels[y, x], dtype=np.uint8)
        contours, _ = cv2.findContours(component, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            continue
        contour = max(contours, key=cv2.contourArea)
        if cv2.contourArea(contour) < 4:
            continue
        # External boundary represents a parcel; interior mask holes are intentionally filled.
        contour = cv2.approxPolyDP(contour, 0.75, True).reshape(-1, 2)
        if len(contour) < 3:
            continue
        height, width = mask.shape
        ring = [pixel_lonlat(float(px), float(py), bounds, width, height) for px, py in contour]
        ring.append(ring[0])
        return {
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [ring]},
            "properties": {
                "source": "sam2.1", "modelScore": float(scores[index]),
                "touchesImageEdge": bool(component[0].any() or component[-1].any()
                                         or component[:, 0].any() or component[:, -1].any()),
            },
        }
    return None


@router.post("/gis/delineate/sam2")
def segment(request: SegmentRequest):
    if not _lock.acquire(blocking=False):
        raise HTTPException(429, "SAM 2.1 正在处理其他请求，请稍后再试")
    try:
        if not os.getenv("SAM2_CHECKPOINT", "").strip():
            raise HTTPException(503, "SAM 2.1 尚未配置：请安装分割依赖并设置 SAM2_CHECKPOINT 权重路径")
        import numpy as np
        import torch
        from PIL import Image, UnidentifiedImageError
        try:
            raw = base64.b64decode(request.imageBase64, validate=True)
            with Image.open(io.BytesIO(raw)) as image:
                if not (16 <= image.width <= 2048 and 16 <= image.height <= 2048):
                    raise HTTPException(400, "影像宽高必须在 16～2048 像素之间")
                pixels = np.array(image.convert("RGB"))
        except (ValueError, binascii.Error, UnidentifiedImageError, OSError, Image.DecompressionBombError):
            raise HTTPException(400, "影像无效，请提供 Base64 编码的图片") from None
        point = click_pixel(request, pixels.shape[1], pixels.shape[0])
        predictor = get_predictor()
        with torch.inference_mode():
            predictor.set_image(pixels)
            masks, scores, _ = predictor.predict(
                point_coords=np.array([point], dtype=np.float32),
                point_labels=np.array([1], dtype=np.int32), multimask_output=True)
        feature = mask_feature(masks, scores, point, request.bounds)
        return {"type": "FeatureCollection", "features": [feature] if feature else []}
    except HTTPException:
        raise
    except ImportError:
        raise HTTPException(503, "SAM 2.1 依赖未安装，请按 python-agent/README.md 安装 requirements-sam2.txt") from None
    except Exception:
        logger.exception("SAM 2.1 inference failed")
        raise HTTPException(500, "SAM 2.1 推理失败，请检查模型配置、权重和 Python 服务日志") from None
    finally:
        _lock.release()
