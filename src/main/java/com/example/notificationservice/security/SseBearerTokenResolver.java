package com.example.notificationservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.StringUtils;

public class SseBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        // 1) Ưu tiên chuẩn: Authorization: Bearer <token>
        String token = headerResolver.resolve(request);
        if (StringUtils.hasText(token)) return token;

        // 2) Chỉ fallback query param cho SSE endpoints (GET /api/v1/sse/**)
        String path = request.getRequestURI();
        if (!"GET".equalsIgnoreCase(request.getMethod())) return null;
        if (path == null || !path.startsWith("/api/v1/sse/")) return null;

        // (tuỳ chọn) chỉ cho connect + subscribe
        if (!(path.endsWith("/connect") || path.contains("/subscribe/"))) return null;

        String qp = request.getParameter("token");
        if (!StringUtils.hasText(qp)) qp = request.getParameter("access_token");
        if (!StringUtils.hasText(qp)) return null;

        // chặn payload quá dài (tránh log abuse)
        if (qp.length() > 4096) return null;

        return qp;
    }
}
