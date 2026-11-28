package com.sciencebot.pos.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.sciencebot.pos.businesses.BusinessFacade> businessFacadeProvider;

    @Mock
    private com.sciencebot.pos.businesses.BusinessFacade businessFacade;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter.setCookieName("jwt_token");
        when(businessFacadeProvider.getIfAvailable()).thenReturn(businessFacade);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_AuthenticatesViaAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-header-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserDetails userDetails = User.builder()
                .username("admin")
                .password("encodedPassword")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")))
                .build();

        when(jwtService.extractUsername("valid-header-token")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid-header-token", userDetails)).thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void doFilterInternal_AuthenticatesViaHttpOnlyCookieWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt_token", "valid-cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserDetails userDetails = User.builder()
                .username("seller01")
                .password("encodedPassword")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SELLER")))
                .build();

        when(jwtService.extractUsername("valid-cookie-token")).thenReturn("seller01");
        when(userDetailsService.loadUserByUsername("seller01")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid-cookie-token", userDetails)).thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("seller01", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    /** Autentica `username` (de un local con `storeStatus`) con el token "tok" en la cabecera. */
    private MockHttpServletRequest requestOf(String username, String role, String storeStatus, String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer tok");
        PosUserDetails userDetails = new PosUserDetails(username, "encoded", true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), 9L, storeStatus);
        when(jwtService.extractUsername("tok")).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        when(jwtService.isTokenValid("tok", userDetails)).thenReturn(true);
        when(jwtService.extractStoreId("tok")).thenReturn(9L);
        when(jwtService.extractRole("tok")).thenReturn(role);
        return request;
    }

    /** Sesión de un dueño (local de origen 9, ACTIVE) que está trabajando en la sucursal 10. */
    private MockHttpServletRequest branchSessionOf(String method, String path) {
        MockHttpServletRequest request = requestOf("owner", "ADMINISTRATOR", "ACTIVE", method, path);
        when(jwtService.extractStoreId("tok")).thenReturn(10L);
        return request;
    }

    @Test
    void doFilterInternal_Branch_OfTheOwnersBusiness_Passes() throws ServletException, IOException {
        MockHttpServletRequest request = branchSessionOf("GET", "/api/v1/sales");
        when(businessFacade.accessibleBranchStatus("owner", 10L)).thenReturn(java.util.Optional.of("ACTIVE"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_Branch_NotOfTheUsersBusiness_Gets403StoreAccessDenied() throws ServletException, IOException {
        MockHttpServletRequest request = branchSessionOf("GET", "/api/v1/sales");
        when(businessFacade.accessibleBranchStatus("owner", 10L)).thenReturn(java.util.Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"error\":\"StoreAccessDenied\""));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_Branch_NotActive_Gets403StoreNotActiveEvenIfTheHomeStoreIsActive() throws ServletException, IOException {
        MockHttpServletRequest request = branchSessionOf("GET", "/api/v1/sales");
        when(businessFacade.accessibleBranchStatus("owner", 10L)).thenReturn(java.util.Optional.of("PENDING_VERIFICATION"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"error\":\"StoreNotActive\""));
        assertTrue(response.getContentAsString().contains("\"status\":\"PENDING_VERIFICATION\""));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_Branch_ActiveWorksEvenIfTheHomeStoreIsSuspended() throws ServletException, IOException {
        MockHttpServletRequest request = requestOf("owner", "ADMINISTRATOR", "SUSPENDED", "GET", "/api/v1/sales");
        when(jwtService.extractStoreId("tok")).thenReturn(10L);
        when(businessFacade.accessibleBranchStatus("owner", 10L)).thenReturn(java.util.Optional.of("ACTIVE"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_Branch_LetsTheOwnerListBranchesWhileTheBranchDoesNotOperate() throws ServletException, IOException {
        MockHttpServletRequest request = branchSessionOf("GET", "/api/v1/business/me");
        when(businessFacade.accessibleBranchStatus("owner", 10L)).thenReturn(java.util.Optional.of("SUSPENDED"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_Branch_AuthPathsStayReachable_WithoutAskingTheBusiness() throws ServletException, IOException {
        MockHttpServletRequest request = branchSessionOf("POST", "/api/v1/auth/switch-store");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(businessFacade, never()).accessibleBranchStatus(any(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_Branch_WhenTheAccessCheckThrows_FailsClosed() throws ServletException, IOException {
        MockHttpServletRequest request = branchSessionOf("GET", "/api/v1/sales");
        when(businessFacade.accessibleBranchStatus("owner", 10L)).thenThrow(new IllegalStateException("db down"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"error\":\"StoreAccessDenied\""));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_Branch_WithoutTheBusinessModule_FailsClosed() throws ServletException, IOException {
        MockHttpServletRequest request = branchSessionOf("GET", "/api/v1/sales");
        when(businessFacadeProvider.getIfAvailable()).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "SELLER, SUSPENDED, GET, /api/v1/products",
            "SUPERVISOR, INACTIVE, POST, /api/v1/purchases",
            "ADMINISTRATOR, PENDING_VERIFICATION, GET, /api/v1/sales",
            "ADMINISTRATOR, REJECTED, GET, /api/v1/stores/me/template",
            "ADMINISTRATOR, REJECTED, PUT, /api/v1/stores/me/resubmit",
            "ADMINISTRATOR, SUSPENDED, GET, /api/v1/stores/me/plans",
    })
    void doFilterInternal_StoreRoleOfANonActiveStore_Gets403StoreNotActive(String role, String status, String method, String path)
            throws ServletException, IOException {
        MockHttpServletRequest request = requestOf("user", role, status, method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"error\":\"StoreNotActive\""));
        assertTrue(response.getContentAsString().contains("\"status\":\"" + status + "\""));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "ADMINISTRATOR, REJECTED, GET, /api/v1/stores/me",
            "ADMINISTRATOR, REJECTED, PUT, /api/v1/stores/me",
            "ADMINISTRATOR, REJECTED, POST, /api/v1/stores/me/resubmit",
            "ADMINISTRATOR, PENDING_VERIFICATION, GET, /api/v1/stores/me/documents",
            "ADMINISTRATOR, PENDING_VERIFICATION, POST, /api/v1/stores/me/documents",
            "SELLER, SUSPENDED, GET, /api/v1/stores/me/status",
            "SELLER, SUSPENDED, POST, /api/v1/auth/logout",
            "SUPERVISOR, INACTIVE, GET, /api/v1/auth/me",
            "SELLER, SUSPENDED, GET, /api/v1/legal/terms",
            "SELLER, SUSPENDED, POST, /api/v1/support/tickets",
    })
    void doFilterInternal_StoreRoleOfANonActiveStore_CanStillUseTheStoreSetupPaths(String role, String status, String method, String path)
            throws ServletException, IOException {
        MockHttpServletRequest request = requestOf("user", role, status, method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilterInternal_StoreRoleOfAnActiveStore_PassesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = requestOf("seller01", "SELLER", "ACTIVE", "GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilterInternal_StoreWithUnknownStatus_IsTreatedAsNotActive() throws ServletException, IOException {
        MockHttpServletRequest request = requestOf("seller01", "SELLER", null, "GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_NoTokenPresent_ContinuesChainWithoutAuthentication() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }
}
