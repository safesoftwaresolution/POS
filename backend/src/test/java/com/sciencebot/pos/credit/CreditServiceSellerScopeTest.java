package com.sciencebot.pos.credit;

import com.sciencebot.pos.audit.AuditFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.credit.internal.entities.CreditAccount;
import com.sciencebot.pos.credit.internal.mappers.CreditMovementMapper;
import com.sciencebot.pos.credit.internal.repositories.CreditAccountReadDao;
import com.sciencebot.pos.credit.internal.repositories.CreditAccountRepository;
import com.sciencebot.pos.credit.internal.repositories.CreditMovementRepository;
import com.sciencebot.pos.credit.internal.services.CreditServiceImpl;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Un vendedor solo ve las cuentas de fiado a las que él les fió. */
class CreditServiceSellerScopeTest {

    private static final Long STORE = 1L;
    private static final Long ANA = 7L;

    @Mock private CreditAccountRepository accountRepository;
    @Mock private CreditMovementRepository movementRepository;
    @Mock private CreditAccountReadDao readDao;
    @Mock private CustomerFacade customerFacade;
    @Mock private UserFacade userFacade;
    @Mock private AuditFacade auditFacade;

    private CreditServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CreditServiceImpl(accountRepository, movementRepository, readDao, new CreditMovementMapper(),
                customerFacade, userFacade, auditFacade);
        TenantContext.setStoreId(STORE);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, Long userId, String role) {
        UserDto user = mock(UserDto.class);
        when(user.id()).thenReturn(userId);
        when(userFacade.findByUsername(username)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void aSellersListIsLimitedToTheAccountsTheyCharged() {
        loginAs("ana", ANA, "SELLER");
        when(readDao.findAccounts(any(), any(), anyBoolean(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listAccounts(null, true, PageRequest.of(0, 20));

        verify(readDao).findAccounts(eq(STORE), eq(null), eq(true), eq(ANA), any());
    }

    @Test
    void anAdministratorsListIsNotLimited() {
        loginAs("boss", 1L, "ADMINISTRATOR");
        when(readDao.findAccounts(any(), any(), anyBoolean(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listAccounts("juan", false, PageRequest.of(0, 20));

        verify(readDao).findAccounts(eq(STORE), eq("juan"), eq(false), eq(null), any());
    }

    @Test
    void aSellerCannotReadTheMovementsOfAnAccountSomeoneElseCharged() {
        loginAs("ana", ANA, "SELLER");
        CreditAccount account = new CreditAccount();
        account.setId(50L);
        when(customerFacade.getById(5L)).thenReturn(Optional.of(mock(CustomerDto.class)));
        when(accountRepository.findByStoreIdAndCustomerId(STORE, 5L)).thenReturn(Optional.of(account));
        when(movementRepository.existsByStoreIdAndAccountIdAndTypeAndUserId(STORE, 50L, CreditMovementType.CHARGE, ANA))
                .thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.getMovements(5L, PageRequest.of(0, 10)));
        verify(movementRepository, never()).findByStoreIdAndAccountIdOrderByCreatedAtDescIdDesc(any(), any(), any());
    }

    @Test
    void aSellerReadsTheMovementsOfAnAccountTheyCharged() {
        loginAs("ana", ANA, "SELLER");
        CreditAccount account = new CreditAccount();
        account.setId(50L);
        when(customerFacade.getById(5L)).thenReturn(Optional.of(mock(CustomerDto.class)));
        when(accountRepository.findByStoreIdAndCustomerId(STORE, 5L)).thenReturn(Optional.of(account));
        when(movementRepository.existsByStoreIdAndAccountIdAndTypeAndUserId(STORE, 50L, CreditMovementType.CHARGE, ANA))
                .thenReturn(true);
        when(movementRepository.findByStoreIdAndAccountIdOrderByCreatedAtDescIdDesc(eq(STORE), eq(50L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getMovements(5L, PageRequest.of(0, 10));

        verify(movementRepository).findByStoreIdAndAccountIdOrderByCreatedAtDescIdDesc(eq(STORE), eq(50L), any());
    }
}
