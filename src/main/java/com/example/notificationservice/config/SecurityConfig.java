package com.example.notificationservice.config;

import com.example.notificationservice.security.SseBearerTokenResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    // ===== 0) CORS Filter - chạy TRƯỚC tất cả security filters =====
    @Bean
    @Order(-1)
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = createCorsConfig();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    // ===== 1) Actuator: public health/info =====
    @Bean
    @Order(0)
    public SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .anyRequest().hasRole("ADMIN")
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    // ===== 2) App APIs =====
    @Bean
    @Order(1)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // === QUAN TRỌNG: Permit all OPTIONS requests (preflight) ===
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // public endpoints
                        .requestMatchers("/ws/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // admin endpoints
                        .requestMatchers("/api/v1/templates/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/redis/test/**").hasRole("ADMIN")

                        // user endpoints
                        .requestMatchers("/api/v1/preferences/**").authenticated()
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        .requestMatchers("/api/v1/sse/**").authenticated()
                        .requestMatchers("/api/v1/auth/test/**").authenticated()

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new SseBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    // ===== CORS Configuration =====
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", createCorsConfig());
        return src;
    }

    private CorsConfiguration createCorsConfig() {
        CorsConfiguration cfg = new CorsConfiguration();

        // Parse allowed origins từ config
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        cfg.setAllowedOrigins(origins);

        // Cho phép tất cả HTTP methods
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));

        // Headers - thêm đầy đủ các headers cần thiết
        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "Cache-Control",
                "Last-Event-ID"  // Quan trọng cho SSE reconnection
        ));

        // Exposed headers - để frontend có thể đọc
        cfg.setExposedHeaders(List.of(
                "Content-Type",
                "Authorization",
                "X-Total-Count",
                "X-Page-Number",
                "X-Page-Size"
        ));

        // Không cần credentials vì dùng Bearer token
        cfg.setAllowCredentials(false);

        // Cache preflight response 1 giờ
        cfg.setMaxAge(3600L);

        return cfg;
    }

    // ===== JWT authorities converter =====
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthoritiesClaimName("scope");
        scopesConverter.setAuthorityPrefix("SCOPE_");

        Converter<Jwt, Collection<GrantedAuthority>> custom = jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // scopes: "scope" / "scp"
            authorities.addAll(scopesConverter.convert(jwt));
            Object scp = jwt.getClaims().get("scp");
            if (scp instanceof Collection<?> c) {
                for (Object it : c) {
                    if (it instanceof String s && !s.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
                    }
                }
            } else if (scp instanceof String s) {
                for (String p : s.split("\\s+|,")) {
                    if (!p.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + p.trim()));
                }
            }

            // roles claim
            Object rolesObj = jwt.getClaims().get("roles");
            normalizeRoles(rolesObj).forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));

            // casdoor tag / isAdmin
            String tag = jwt.getClaimAsString("tag");
            Boolean isAdmin = jwt.getClaim("isAdmin");
            if ("staff".equalsIgnoreCase(tag) || "admin".equalsIgnoreCase(tag) || Boolean.TRUE.equals(isAdmin)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            // default role
            boolean hasRole = authorities.stream().anyMatch(a -> a.getAuthority().startsWith("ROLE_"));
            if (!hasRole) authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            return authorities.stream().distinct().collect(Collectors.toList());
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(custom);
        return converter;
    }

    // ===== Helper methods =====
    private static List<String> normalizeRoles(Object rolesClaim) {
        List<String> out = new ArrayList<>();
        if (rolesClaim instanceof Collection<?> coll) {
            for (Object it : coll) {
                if (it instanceof String s) addRole(out, s);
                else if (it instanceof Map<?, ?> m) {
                    Object v = getMapValueIgnoreCase(m, "name", "role", "authority", "value", "code", "key", "displayname");
                    if (v instanceof String s) addRole(out, s);
                }
            }
        } else if (rolesClaim instanceof String s) {
            addRole(out, s);
        }
        return out;
    }

    private static Object getMapValueIgnoreCase(Map<?, ?> map, String... keys) {
        Map<String, Object> lower = new HashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() instanceof String k) lower.put(k.toLowerCase(Locale.ROOT), e.getValue());
        }
        for (String k : keys) {
            Object v = lower.get(k.toLowerCase(Locale.ROOT));
            if (v != null) return v;
        }
        return null;
    }

    private static void addRole(List<String> out, String raw) {
        String r = normalizeRoleName(raw);
        if (r != null) out.add(r);
    }

    private static String normalizeRoleName(String name) {
        if (name == null) return null;
        String r = name.trim();
        if (r.isEmpty()) return null;
        r = r.replaceAll("[^a-zA-Z0-9]+", "_").toUpperCase(Locale.ROOT);
        if (!r.startsWith("ROLE_")) r = "ROLE_" + r;
        return r;
    }
}