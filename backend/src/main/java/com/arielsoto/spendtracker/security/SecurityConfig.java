package com.arielsoto.spendtracker.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.arielsoto.spendtracker.loggin.ClientIpResolver;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final CustomOAuth2UserService customOAuth2UserService;

    private final CustomOidcUserService customOidcUserService;

    @Value("#{'${app.cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    @Value("#{'${app.cors.allowed-methods}'.split(',')}")
    private List<String> allowedMethods;

    @Value("#{'${app.cors.allowed-headers}'.split(',')}")
    private List<String> allowedHeaders;

    @Value("${app.oauth2.default-success-url}")
    private String oauth2DefaultSuccessUrl;

    @Value("${app.logout.logout-url}")
    private String logoutUrl;

    private final SecurityAuditLogger auditLogger;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {        
        return http

            .sessionManagement(session -> session
                .sessionFixation(sessionFixation ->
                    sessionFixation.migrateSession()
                )
            )
            
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                .csrfTokenRequestHandler(
                    new CsrfTokenRequestAttributeHandler()
                )
            )

            .cors(Customizer.withDefaults())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", 
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            
            .exceptionHandling(exception -> exception

                .authenticationEntryPoint((request, response, ex) -> {

                    auditLogger.unauthorized(
                        request.getRequestURI(),
                        ClientIpResolver.resolve(request)
                    );

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                })

                .accessDeniedHandler((request, response, ex) -> {

                    auditLogger.accessDenied(
                        request.getRequestURI(),
                        ClientIpResolver.resolve(request)
                    );

                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                })
            )
            
            .oauth2Login(oauth -> oauth
                 .successHandler((request, response, authentication) -> {

                    auditLogger.loginSuccess(
                        authentication.getName(),
                        ClientIpResolver.resolve(request)
                    );

                    response.sendRedirect(oauth2DefaultSuccessUrl);
                })

                .failureHandler((request, response, exception) -> {

                    auditLogger.loginFailed(
                        exception.getClass().getSimpleName(),
                        ClientIpResolver.resolve(request)
                    );

                    response.setStatus(401);
                })

                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService)
                )
                
            )
            
            .logout(logout -> logout
                .logoutUrl(logoutUrl)
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies(
                    "JSESSIONID",
                    "XSRF-TOKEN"
                )
                .logoutSuccessHandler((request, response, authentication) -> {

                    if (authentication != null) {
                        auditLogger.logout(authentication.getName());
                    }

                    response.setStatus(HttpServletResponse.SC_OK);
                })
            )
            
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);

        config.setAllowedOrigins(allowedOrigins);
        
        config.setAllowedMethods(allowedMethods);

        config.setAllowedHeaders(allowedHeaders);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

}
