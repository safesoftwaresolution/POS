package com.sciencebot.pos.config.security;

import com.sciencebot.pos.businesses.BusinessFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Value("${security.jwt.cookie-name:jwt_token}")
    private String cookieName = "jwt_token";

    /** Lazy, to avoid a cycle with the users module that already provides {@code userDetailsService}. */
    private final ObjectProvider<BusinessFacade> businessFacadeProvider;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService,
                                   ObjectProvider<BusinessFacade> businessFacadeProvider) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.businessFacadeProvider = businessFacadeProvider;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String jwt = extractToken(request);

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        final String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                    if (userDetails.isEnabled() && jwtService.isTokenValid(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        Long storeId = jwtService.extractStoreId(jwt);
                        TenantContext.setStoreId(storeId);

                        if (storeId == null && "ADMINISTRATOR".equals(jwtService.extractRole(jwt))
                                && !isOnboardingPath(request)) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"error\":\"StoreOnboardingRequired\",\"message\":\"Completa el registro de tu local antes de continuar.\"}");
                            return;
                        }

                        // Sucursal activa distinta del local del usuario (cambio de sucursal del dueño de un negocio):
                        // el JWT firmado no basta, porque lo pudieron sacar del negocio después de emitirlo. Se
                        // comprueba en cada petición y el estado que cuenta es el de esa sucursal, no el del local de origen.
                        String effectiveStatus = userDetails instanceof PosUserDetails p ? p.getStoreStatus() : null;
                        if (storeId != null && userDetails instanceof PosUserDetails posUser
                                && posUser.getStoreId() != null && !storeId.equals(posUser.getStoreId())
                                && !"SUPER_ADMIN".equals(jwtService.extractRole(jwt))
                                && !isAuthPath(request)) {
                            java.util.Optional<String> branchStatus = branchStatus(username, storeId);
                            if (branchStatus.isEmpty()) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.setCharacterEncoding("UTF-8");
                                response.getWriter().write("{\"error\":\"StoreAccessDenied\","
                                        + "\"message\":\"Ya no tienes acceso a esa sucursal. Vuelve a elegir una.\"}");
                                return;
                            }
                            effectiveStatus = branchStatus.get();
                        }

                        // Un local que no está ACTIVE (en revisión, rechazado, suspendido, inactivo) no opera:
                        // su personal solo puede consultar o corregir el local y usar autenticación y soporte.
                        if (storeId != null && userDetails instanceof PosUserDetails
                                && !"SUPER_ADMIN".equals(jwtService.extractRole(jwt))
                                && !"ACTIVE".equals(effectiveStatus)
                                && !isStoreSetupPath(request)) {
                            String status = effectiveStatus;
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"error\":\"StoreNotActive\",\"status\":"
                                    + (status == null ? "null" : "\"" + status + "\"")
                                    + ",\"message\":\"Tu local no está activo. Revisa su estado para continuar.\"}");
                            return;
                        }
                    }
                } catch (Exception e) {
                    // Ignored
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private java.util.Optional<String> branchStatus(String username, Long storeId) {
        BusinessFacade businessFacade = businessFacadeProvider == null ? null : businessFacadeProvider.getIfAvailable();
        if (businessFacade == null) {
            return java.util.Optional.empty();
        }
        try {
            return businessFacade.accessibleBranchStatus(username, storeId);
        } catch (RuntimeException e) {
            // El catch general de doFilterInternal ignora errores y deja pasar la petición ya autenticada: aquí un
            // fallo al verificar el acceso debe negarlo, no permitirlo.
            logger.warn("No se pudo verificar el acceso a la sucursal " + storeId + "; se niega.", e);
            return java.util.Optional.empty();
        }
    }

    private boolean isAuthPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/auth/");
    }

    /**
     * Rutas permitidas a un ADMINISTRATOR que aun no ha creado su local (storeId nulo en el JWT):
     * autenticacion y el propio endpoint de registro/consulta del local.
     */
    private boolean isOnboardingPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith(request.getContextPath() + "/api/v1/auth/")) {
            return true;
        }
        String storesPath = request.getContextPath() + "/api/v1/stores";
        if (path.equals(storesPath) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        return path.equals(storesPath + "/me") && "GET".equalsIgnoreCase(method);
    }

    /**
     * Rutas permitidas al personal de un local que no está ACTIVE: las de onboarding, consultar y corregir el
     * local, sus documentos KYC, reenviarlo a revisión, su estado, autenticación, documentos legales y soporte.
     * Lo que no es de la API (Swagger, estáticos) no se bloquea aquí.
     */
    private boolean isStoreSetupPath(HttpServletRequest request) {
        String api = request.getContextPath() + "/api/";
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!path.startsWith(api) || isOnboardingPath(request)) {
            return true;
        }
        String v1 = api + "v1";
        if (path.startsWith(v1 + "/legal/") || path.startsWith(v1 + "/support/")) {
            return true;
        }
        // Para poder volver a otra sucursal estando en una que no opera, el dueño debe poder listarlas.
        if (path.equals(v1 + "/business/me")) {
            return "GET".equalsIgnoreCase(method);
        }
        String me = v1 + "/stores/me";
        if (path.equals(me)) {
            return "GET".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method);
        }
        if (path.equals(me + "/status")) {
            return "GET".equalsIgnoreCase(method);
        }
        if (path.equals(me + "/documents")) {
            return "GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method);
        }
        return path.equals(me + "/resubmit") && "POST".equalsIgnoreCase(method);
    }

    private String extractToken(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}