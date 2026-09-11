import os
import unittest
from unittest.mock import patch

import numpy as np
from fastapi import FastAPI
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.sam2_service import Bounds, SegmentRequest, click_pixel, pixel_lonlat, mask_feature, router, _lock


class Sam2Tests(unittest.TestCase):
    def setUp(self):
        self.bounds = Bounds(west=110, east=111, south=60, north=61)
        self.payload = dict(imageBase64="invalid", bounds=self.bounds.model_dump(),
                            longitude=110.5, latitude=60.5)
        app = FastAPI()
        app.include_router(router)
        self.client = TestClient(app)

    def test_web_mercator_pixel_roundtrip(self):
        for x, y in [(0, 0), (127, 63), (255, 255)]:
            lon, lat = pixel_lonlat(x, y, self.bounds, 256, 256)
            request = SegmentRequest(**{**self.payload, "longitude": lon, "latitude": lat})
            self.assertEqual(click_pixel(request, 256, 256), (x, y))
        # Latitude interpolation must be nonlinear at high latitudes.
        _, lat = pixel_lonlat(127.5, 127.5, self.bounds, 256, 256)
        self.assertGreater(abs(lat - 60.5), 0.001)

    def test_reject_invalid_bounds_and_click(self):
        with self.assertRaises(ValidationError):
            Bounds(west=111, east=110, south=60, north=61)
        self.assertEqual(self.client.post('/gis/delineate/sam2', json={
            **self.payload, 'longitude': 112}).status_code, 422)

    def test_select_clicked_component_not_unrelated_high_score_mask(self):
        masks = np.zeros((2, 32, 32), dtype=np.uint8)
        masks[0, 3:10, 3:10] = 1
        masks[0, 20:30, 20:30] = 1  # Larger disconnected region must be discarded.
        masks[1, 20:30, 20:30] = 1  # Higher score but does not contain click.
        feature = mask_feature(masks, np.array([0.8, 0.99]), (5, 5), self.bounds)
        self.assertEqual(feature['properties']['modelScore'], 0.8)
        self.assertFalse(feature['properties']['touchesImageEdge'])
        ring = feature['geometry']['coordinates'][0]
        self.assertEqual(ring[0], ring[-1])
        self.assertTrue(all(lon < 110.4 for lon, _ in ring))

    def test_empty_and_edge_masks(self):
        mask = np.zeros((1, 32, 32), dtype=np.uint8)
        self.assertIsNone(mask_feature(mask, [0.9], (5, 5), self.bounds))
        mask[0, :10, :10] = 1
        self.assertTrue(mask_feature(mask, [0.9], (5, 5), self.bounds)
                        ['properties']['touchesImageEdge'])

    def test_unconfigured_service_and_busy_service(self):
        with patch.dict(os.environ, {'SAM2_CHECKPOINT': ''}):
            response = self.client.post('/gis/delineate/sam2', json=self.payload)
        self.assertEqual(response.status_code, 503)
        self.assertIn('SAM2_CHECKPOINT', response.json()['detail'])
        with _lock:
            self.assertEqual(self.client.post('/gis/delineate/sam2', json=self.payload).status_code, 429)
        self.assertFalse(_lock.locked())

    def test_full_app_keeps_agent_routes_without_sam_dependencies(self):
        from app.main import app
        client = TestClient(app)
        with patch.dict(os.environ, {'AI_API_KEY': '', 'SAM2_CHECKPOINT': ''}):
            health = client.get('/health').json()
            self.assertEqual(health['status'], 'UP')
            self.assertFalse(health['sam2Configured'])
            review = client.post('/agent/review', json={
                'user_id': 1, 'message': '测试', 'evidences': ['已有证据'],
            })
            self.assertEqual(review.status_code, 200)
            self.assertIn('已有证据', review.json()['reply'])
            self.assertEqual(review.json()['mode'], 'python-local')
            self.assertEqual(client.post('/gis/delineate/sam2', json=self.payload).status_code, 503)


if __name__ == '__main__':
    unittest.main()
