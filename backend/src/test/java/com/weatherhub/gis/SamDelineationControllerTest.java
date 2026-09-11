package com.weatherhub.gis;

import com.sun.net.httpserver.HttpServer;
import com.weatherhub.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SamDelineationControllerTest {
    private final SamDelineationController.Request request = new SamDelineationController.Request(
            "aW1hZ2U=", new SamDelineationController.Bounds(110.0, 60.0, 111.0, 61.0), 110.5, 60.5);

    @Test
    void forwardsImageAndPromptAndReturnsGeojson() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<String> upgrade = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gis/delineate/sam2", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            byte[] response = "{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var controller = new SamDelineationController("http://127.0.0.1:" + server.getAddress().getPort());
            var result = controller.delineate(request);
            assertEquals("FeatureCollection", result.getData().get("type").asString());
            assertTrue(received.get().contains("\"longitude\":110.5"));
            assertTrue(received.get().contains("aW1hZ2U="));
            assertNull(upgrade.get(), "Uvicorn requires HTTP/1.1 without an h2c upgrade");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesActionablePythonError() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gis/delineate/sam2", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"detail\":\"请设置 SAM2_CHECKPOINT\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(503, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var controller = new SamDelineationController("http://127.0.0.1:" + server.getAddress().getPort());
            var error = assertThrows(BusinessException.class, () -> controller.delineate(request));
            assertEquals("请设置 SAM2_CHECKPOINT", error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAM2_TEST_IMAGE", matches = ".+")
    void livePythonServiceReturnsPolygonThroughJavaClient() throws Exception {
        String image = Base64.getEncoder().encodeToString(
                Files.readAllBytes(Path.of(System.getenv("SAM2_TEST_IMAGE"))));
        var controller = new SamDelineationController("http://127.0.0.1:8000");
        var result = controller.delineate(new SamDelineationController.Request(
                image, request.bounds(), request.longitude(), request.latitude()));
        assertEquals("FeatureCollection", result.getData().get("type").asString());
        assertFalse(result.getData().get("features").isEmpty());
        assertEquals("sam2.1", result.getData().get("features").get(0)
                .get("properties").get("source").asString());
    }
}
