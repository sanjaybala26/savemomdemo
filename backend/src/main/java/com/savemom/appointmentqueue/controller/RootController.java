package com.savemom.appointmentqueue.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping({"/", "/api"})
    public ResponseEntity<Map<String, Object>> getApiOverview() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "CareQueue - Healthcare Appointment & Queue Management API");
        response.put("status", "UP");
        response.put("database", "CONNECTED (TiDB Cloud MySQL)");
        response.put("frontendApp", "https://carequeue-frontend.vercel.app");
        response.put("healthCheck", "https://carequeue-backend-0f3l.onrender.com/api/health");
        response.put("publicProvidersData", "https://carequeue-backend-0f3l.onrender.com/api/providers");
        return ResponseEntity.ok(response);
    }
}
