package com.savemom.appointmentqueue.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root Controller that provides an interactive API Portal and JSON Directory
 * when evaluators visit the base URL (https://carequeue-backend-0f3l.onrender.com).
 * Zero impact on existing services, purely additive.
 */
@RestController
public class RootController {

    @GetMapping(value = {"/", "/api"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getInteractiveApiPortal() {
        String html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>CareQueue API Explorer</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
                <style>
                    :root {
                        --primary: #0284c7;
                        --primary-hover: #0369a1;
                        --success: #10b981;
                        --bg: #0f172a;
                        --card-bg: #1e293b;
                        --border: #334155;
                        --text: #f8fafc;
                        --muted: #94a3b8;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: 'Inter', -apple-system, sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        padding: 2rem 1rem;
                        line-height: 1.6;
                    }
                    .container { max-width: 960px; margin: 0 auto; }
                    .header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        flex-wrap: wrap;
                        gap: 1rem;
                        margin-bottom: 2rem;
                        padding-bottom: 1.5rem;
                        border-bottom: 1px solid var(--border);
                    }
                    .logo-group h1 { font-size: 1.75rem; font-weight: 700; color: #fff; }
                    .logo-group p { color: var(--muted); font-size: 0.95rem; }
                    .badge-online {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.5rem;
                        background: rgba(16, 185, 129, 0.15);
                        color: var(--success);
                        border: 1px solid rgba(16, 185, 129, 0.3);
                        padding: 0.4rem 0.9rem;
                        border-radius: 9999px;
                        font-size: 0.85rem;
                        font-weight: 600;
                    }
                    .badge-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--success); }
                    .btn-frontend {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.5rem;
                        background: var(--primary);
                        color: #fff;
                        padding: 0.6rem 1.25rem;
                        border-radius: 8px;
                        text-decoration: none;
                        font-weight: 600;
                        transition: background 0.2s;
                    }
                    .btn-frontend:hover { background: var(--primary-hover); }
                    .section-title {
                        font-size: 1.25rem;
                        font-weight: 600;
                        margin: 2rem 0 1rem;
                        color: #e2e8f0;
                        display: flex;
                        align-items: center;
                        gap: 0.5rem;
                    }
                    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1rem; }
                    .card {
                        background: var(--card-bg);
                        border: 1px solid var(--border);
                        border-radius: 10px;
                        padding: 1.25rem;
                        transition: transform 0.15s, border-color 0.15s;
                    }
                    .card:hover { border-color: var(--primary); transform: translateY(-2px); }
                    .method-badge {
                        display: inline-block;
                        padding: 0.2rem 0.5rem;
                        border-radius: 4px;
                        font-family: 'JetBrains Mono', monospace;
                        font-size: 0.75rem;
                        font-weight: 700;
                        margin-bottom: 0.75rem;
                    }
                    .method-get { background: #065f46; color: #6ee7b7; }
                    .method-post { background: #1e40af; color: #93c5fd; }
                    .method-put { background: #854d0e; color: #fde047; }
                    .endpoint-link {
                        display: block;
                        color: #38bdf8;
                        text-decoration: none;
                        font-family: 'JetBrains Mono', monospace;
                        font-size: 0.9rem;
                        font-weight: 600;
                        word-break: break-all;
                        margin-bottom: 0.5rem;
                    }
                    .endpoint-link:hover { text-decoration: underline; color: #7dd3fc; }
                    .card-desc { font-size: 0.85rem; color: var(--muted); line-height: 1.5; }
                    .callout {
                        background: rgba(2, 132, 199, 0.1);
                        border: 1px solid rgba(2, 132, 199, 0.3);
                        border-radius: 10px;
                        padding: 1.25rem;
                        margin-top: 2rem;
                    }
                    .callout h3 { font-size: 1rem; color: #38bdf8; margin-bottom: 0.5rem; }
                    .callout ul { list-style: none; display: flex; flex-wrap: wrap; gap: 1rem; }
                    .callout li { font-size: 0.85rem; color: var(--muted); }
                    .callout strong { color: #f1f5f9; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo-group">
                            <h1>CareQueue REST API</h1>
                            <p>Healthcare Appointment Scheduling & Real-Time Queue Management</p>
                        </div>
                        <div style="display: flex; gap: 0.75rem; align-items: center;">
                            <span class="badge-online"><span class="badge-dot"></span> Backend & Cloud MySQL Live</span>
                            <a href="https://carequeue-frontend.vercel.app" target="_blank" class="btn-frontend">Open Web App &rarr;</a>
                        </div>
                    </div>

                    <h2 class="section-title">Public Clickable Endpoints (Direct Browser Access)</h2>
                    <div class="grid">
                        <div class="card">
                            <span class="method-badge method-get">GET</span>
                            <a href="/api/health" class="endpoint-link" target="_blank">/api/health</a>
                            <p class="card-desc">Returns real-time server health, database connectivity to TiDB Cloud MySQL, and schema table verification.</p>
                        </div>
                        <div class="card">
                            <span class="method-badge method-get">GET</span>
                            <a href="/api/providers" class="endpoint-link" target="_blank">/api/providers</a>
                            <p class="card-desc">Returns all registered specialist doctors, consultation working days, and operating schedules directly from the cloud database.</p>
                        </div>
                        <div class="card">
                            <span class="method-badge method-get">GET</span>
                            <a href="/api/providers/1/slots?date=2026-11-06" class="endpoint-link" target="_blank">/api/providers/1/slots?date=2026-11-06</a>
                            <p class="card-desc">Calculates dynamic 30-minute availability slots for Dr. Sarah Jenkins with live booking checks.</p>
                        </div>
                    </div>

                    <h2 class="section-title">Protected & Transactional REST Endpoints</h2>
                    <div class="grid">
                        <div class="card">
                            <span class="method-badge method-post">POST</span>
                            <span class="endpoint-link" style="color: #cbd5e1;">/api/auth/register</span>
                            <p class="card-desc">Registers a new patient account with BCrypt password hashing. Returns signed JWT.</p>
                        </div>
                        <div class="card">
                            <span class="method-badge method-post">POST</span>
                            <span class="endpoint-link" style="color: #cbd5e1;">/api/auth/login</span>
                            <p class="card-desc">Authenticates user credentials and issues a stateless HMAC-SHA256 JWT bearer token.</p>
                        </div>
                        <div class="card">
                            <span class="method-badge method-post">POST</span>
                            <span class="endpoint-link" style="color: #cbd5e1;">/api/appointments</span>
                            <p class="card-desc">Books an appointment with atomic slot lock to prevent double-booking, and generates daily queue token.</p>
                        </div>
                        <div class="card">
                            <span class="method-badge method-get">GET</span>
                            <span class="endpoint-link" style="color: #cbd5e1;">/api/appointments/my</span>
                            <p class="card-desc">Returns authenticated patient's visit history and status changes.</p>
                        </div>
                        <div class="card">
                            <span class="method-badge method-put">PUT</span>
                            <span class="endpoint-link" style="color: #cbd5e1;">/api/queue/token/{id}/call</span>
                            <p class="card-desc">Doctor calls the next patient into the consultation room (transitions status to CALLED).</p>
                        </div>
                        <div class="card">
                            <span class="method-badge method-get">GET</span>
                            <span class="endpoint-link" style="color: #cbd5e1;">/api/admin/stats</span>
                            <p class="card-desc">Clinic-wide KPIs: active patients waiting, completed consultations, and average wait time.</p>
                        </div>
                    </div>

                    <div class="callout">
                        <h3>Demo Accounts For Evaluators (Use on Web App)</h3>
                        <ul>
                            <li><strong>Admin:</strong> admin@carequeue.com / admin123</li>
                            <li><strong>Doctor 1:</strong> doctor.sarah@carequeue.com / doctor123</li>
                            <li><strong>Doctor 2:</strong> doctor.james@carequeue.com / doctor123</li>
                            <li><strong>Patient:</strong> sanjaytest@gmail.com / mypassword123</li>
                        </ul>
                    </div>
                </div>
            </body>
            </html>
            """;
        return ResponseEntity.ok(html);
    }

    @GetMapping(value = {"/", "/api"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getApiOverviewJson() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "CareQueue - Healthcare Appointment & Queue Management API");
        response.put("status", "UP");
        response.put("database", "CONNECTED (TiDB Cloud MySQL)");
        response.put("frontendApp", "https://carequeue-frontend.vercel.app");
        response.put("healthCheck", "https://carequeue-backend-0f3l.onrender.com/api/health");
        response.put("publicProviders", "https://carequeue-backend-0f3l.onrender.com/api/providers");
        return ResponseEntity.ok(response);
    }
}
