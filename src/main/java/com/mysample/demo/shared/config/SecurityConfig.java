package com.mysample.demo.shared.config;

import com.mysample.demo.shared.error.security.ProblemDetailAccessDeniedHandler;
import com.mysample.demo.shared.error.security.ProblemDetailAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Inbound security. Exactly one filter chain is active:
 * <ul>
 *   <li>default (local, uat, prod): JWT resource server. Local validates tokens from the
 *       Keycloak in compose.yaml (issuer-uri in application-local.yml).</li>
 *   <li>"noauth" (opt-in, laptops only): no authentication.</li>
 * </ul>
 * URL rules only separate public from protected paths. Business authorization (which scope
 * each endpoint needs: customers:*, accounts:*) is declared next to the endpoint with
 * {@code @PreAuthorize}, so a new module needs no change here.
 * <p>
 * 401/403 from the filter chain are routed to GlobalExceptionHandler by the
 * {@code shared.error.security} handlers, so they share the problem+json shape.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            // Probes and scraping. Restrict /actuator/prometheus at the network level too
            // (or move actuator to management.server.port and keep it off the ingress).
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    /**
     * Authorities given to every anonymous caller under "noauth": all business scopes, so every
     * {@code @PreAuthorize} passes. Add the scopes of a new module here. ops:read is left out
     * on purpose: protected actuator endpoints stay closed even on a laptop.
     */
    private static final String[] NOAUTH_AUTHORITIES = {
            "SCOPE_customers:read", "SCOPE_customers:write",
            "SCOPE_accounts:read", "SCOPE_accounts:write"
    };

    /** Every profile except "noauth": stateless JWT resource server. */
    @Bean
    @Profile("!noauth")
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http,
                                               ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                               ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        statelessApi(http, authenticationEntryPoint, accessDeniedHandler)
                .authorizeHttpRequests(auth -> auth
                        // Boot's error dispatch must not be masked as an empty 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Any actuator endpoint exposed later is protected by default.
                        .requestMatchers("/actuator/**").hasAuthority("SCOPE_ops:read")
                        // /api/v1/customers/** and /api/v1/accounts/**: token required here,
                        // scope checked by @PreAuthorize on each endpoint
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Opt-in "noauth" profile, to try the API without Keycloak: anonymous callers get every
     * business scope. {@code @PreAuthorize} still runs. Never activate it on a shared environment.
     */
    @Bean
    @Profile("noauth")
    SecurityFilterChain noAuthSecurityFilterChain(HttpSecurity http,
                                                  ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                                  ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        log.warn("NOAUTH PROFILE: API authentication is disabled; every caller has {}",
                String.join(", ", NOAUTH_AUTHORITIES));
        statelessApi(http, authenticationEntryPoint, accessDeniedHandler)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .anonymous(anonymous -> anonymous.authorities(NOAUTH_AUTHORITIES));
        return http.build();
    }

    private static HttpSecurity statelessApi(HttpSecurity http,
                                             ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                             ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // stateless API with bearer tokens, no cookies
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
    }
}
