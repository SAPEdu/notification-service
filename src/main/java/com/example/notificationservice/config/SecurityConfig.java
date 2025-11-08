package com.example.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    // === 1) Chain cho ACTUATOR: permitAll, không đụng JWT ===
    @Bean
    @Order(0)
    public SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(rc -> rc.disable())
                .securityContext(sc -> sc.disable());
        return http.build();
    }

    // === 2) Chain cho ứng dụng: yêu cầu JWT ===
    @Bean
    @Order(1)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // public
                        .requestMatchers("/ws/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // admin
                        .requestMatchers("/api/v1/templates/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // user
                        .requestMatchers("/api/v1/preferences/**").authenticated()
                        .requestMatchers("/api/v1/sse/**").authenticated()

                        // còn lại
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );
        return http.build();
    }

    // === JWT Decoder ===
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    // === Converter: scopes + Casdoor roles (object array) + tag/isAdmin ===
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Scopes chuẩn (scope/scp) -> SCOPE_*
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthoritiesClaimName("scope");
        scopesConverter.setAuthorityPrefix("SCOPE_");

        Converter<Jwt, Collection<GrantedAuthority>> custom = jwt -> {
            List<GrantedAuthority> out = new ArrayList<>();

            // 1) scopes từ "scope"
            out.addAll(scopesConverter.convert(jwt));

            // 1b) thêm từ "scp" nếu có
            Object scp = jwt.getClaims().get("scp");
            if (scp instanceof Collection<?> c) {
                for (Object it : c) {
                    if (it instanceof String s && !s.isBlank()) {
                        out.add(new SimpleGrantedAuthority("SCOPE_" + s));
                    }
                }
            } else if (scp instanceof String s) {
                for (String p : s.split("\\s+|,")) {
                    if (!p.isBlank()) out.add(new SimpleGrantedAuthority("SCOPE_" + p.trim()));
                }
            }

            // 2) roles từ Casdoor: [{ "name": "Admin", ... }] hoặc [{ "NAME": "ADMIN", ... }]
            Object rolesObj = jwt.getClaims().get("roles");
            normalizeRoles(rolesObj).forEach(r -> out.add(new SimpleGrantedAuthority(r)));

            // 3) Casdoor-specific: tag/isAdmin
            String tag = jwt.getClaimAsString("tag");
            Boolean isAdmin = jwt.getClaim("isAdmin");
            if ("staff".equalsIgnoreCase(tag) || "admin".equalsIgnoreCase(tag) || Boolean.TRUE.equals(isAdmin)) {
                out.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            // 4) nếu chưa có ROLE_* nào, thêm ROLE_USER
            boolean hasRole = out.stream().anyMatch(a -> a.getAuthority().startsWith("ROLE_"));
            if (!hasRole) out.add(new SimpleGrantedAuthority("ROLE_USER"));

            // loại trùng lặp
            return out.stream().distinct().collect(Collectors.toList());
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(custom);
        return converter;
    }

    // === CORS ===
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("*"));
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    // ==== Helpers ====
    /** Chuyển mọi dạng claim "roles" về danh sách ROLE_* - CASE INSENSITIVE */
    private static List<String> normalizeRoles(Object rolesClaim) {
        List<String> out = new ArrayList<>();
        if (rolesClaim instanceof Collection<?> coll) {
            for (Object it : coll) {
                if (it instanceof String s) {
                    addRole(out, s);
                } else if (it instanceof Map<?,?> m) {
                    // FIXED: Case-insensitive key lookup for Casdoor
                    Object v = getMapValueIgnoreCase(m, "name", "role", "authority",
                            "value", "code", "key", "displayname");
                    if (v instanceof String s) {
                        addRole(out, s);
                    }
                }
            }
        } else if (rolesClaim instanceof String s) {
            addRole(out, s);
        }
        return out;
    }

    /**
     * Get value from map with case-insensitive key lookup
     * Tries each key in order, returns first non-null value
     */
    private static Object getMapValueIgnoreCase(Map<?,?> map, String... keys) {
        // Create a case-insensitive lookup map
        Map<String, Object> lowerCaseMap = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                lowerCaseMap.put(key.toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        // Try each key
        for (String key : keys) {
            Object value = lowerCaseMap.get(key.toLowerCase(Locale.ROOT));
            if (value != null) {
                return value;
            }
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

        // Remove non-alphanumeric characters and convert to uppercase
        r = r.replaceAll("[^a-zA-Z0-9]+", "_").toUpperCase(Locale.ROOT);

        // Ensure ROLE_ prefix
        if (!r.startsWith("ROLE_")) r = "ROLE_" + r;

        return r;
    }

    private static Object firstNonNull(Object... arr) {
        for (Object o : arr) if (o != null) return o;
        return null;
    }
}