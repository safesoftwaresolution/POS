package com.sciencebot.pos.customers;

import com.sciencebot.pos.audit.AuditAction;
import com.sciencebot.pos.audit.AuditFacade;
import com.sciencebot.pos.audit.FieldChange;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.internal.entities.Customer;
import com.sciencebot.pos.customers.internal.mappers.CustomerMapper;
import com.sciencebot.pos.customers.internal.repositories.CustomerRepository;
import com.sciencebot.pos.customers.internal.services.CustomerServiceImpl;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CustomerServiceImplTest {

    private static final Long STORE_ID = 1L;
    private static final Long ANA = 7L;
    private static final Long LUIS = 8L;

    @Mock
    private CustomerRepository customerRepository;

    @Spy
    private CustomerMapper customerMapper = new CustomerMapper();

    @Mock
    private UserFacade userFacade;

    @Mock
    private AuditFacade auditFacade;

    @Mock
    private CustomerDeleteValidator deleteValidator;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        customerService = new CustomerServiceImpl(customerRepository, customerMapper, userFacade, auditFacade,
                List.of(deleteValidator));
        TenantContext.setStoreId(STORE_ID);
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

    private Customer customerCreatedBy(Long id, Long creatorId, String creatorName) {
        Customer c = new Customer();
        c.setId(id);
        c.setStoreId(STORE_ID);
        c.setFullName("Maria Lopez");
        c.setIdentification("1234567890");
        c.setEmail("maria@mail.com");
        c.setCreatedBy(creatorId);
        c.setCreatedByUsername(creatorName);
        return c;
    }

    private static UpdateCustomerCommand newName(String name) {
        return new UpdateCustomerCommand(name, "maria@mail.com", null, null);
    }

    @Test
    void createCustomer_RecordsCreatorAndAuditsTheCreation() {
        loginAs("ana", ANA, "SELLER");
        when(customerRepository.existsByStoreIdAndIdentification(STORE_ID, "1234567890")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        CustomerDto result = customerService.createCustomer(
                new CreateCustomerCommand("Maria Lopez", "1234567890", "maria@mail.com", "555-0100", "Calle 1"));

        assertEquals(2L, result.id());
        assertEquals("ana", result.createdByUsername());
        assertEquals("ana", result.updatedByUsername());
        assertTrue(result.editable(), "el creador puede editar su propio cliente");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertEquals(ANA, saved.getValue().getCreatedBy());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, FieldChange>> changes = ArgumentCaptor.forClass(Map.class);
        verify(auditFacade).record(eq("CUSTOMER"), eq(2L), eq(AuditAction.CREATE), eq(ANA), eq("ana"), changes.capture());
        assertEquals("Maria Lopez", changes.getValue().get("fullName").to());
        assertNull(changes.getValue().get("fullName").from());
        assertEquals(13, changes.getValue().get("legalOrganizationId").to(), "también se auditan los valores predeterminados");
    }

    @Test
    void createCustomer_DuplicateIdentification_IsRefused() {
        when(customerRepository.existsByStoreIdAndIdentification(STORE_ID, "1234567890")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> customerService.createCustomer(
                new CreateCustomerCommand("Maria Lopez", "1234567890", null, null, null)));
        verifyNoInteractions(auditFacade);
    }

    @Test
    void updateCustomer_SellerEditsTheirOwnCustomer_AuditsTheDifference() {
        loginAs("ana", ANA, "SELLER");
        Customer own = customerCreatedBy(5L, ANA, "ana");
        when(customerRepository.findById(5L)).thenReturn(Optional.of(own));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDto result = customerService.updateCustomer(5L, newName("Maria Lopez Gomez"));

        assertEquals("Maria Lopez Gomez", result.fullName());
        assertEquals("ana", result.updatedByUsername());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, FieldChange>> changes = ArgumentCaptor.forClass(Map.class);
        verify(auditFacade).record(eq("CUSTOMER"), eq(5L), eq(AuditAction.UPDATE), eq(ANA), eq("ana"), changes.capture());
        assertEquals(Map.of("fullName", new FieldChange("Maria Lopez", "Maria Lopez Gomez")), changes.getValue());
    }

    @Test
    void updateCustomer_SellerCannotEditACustomerCreatedBySomeoneElse() {
        loginAs("ana", ANA, "SELLER");
        when(customerRepository.findById(5L)).thenReturn(Optional.of(customerCreatedBy(5L, LUIS, "luis")));

        assertThrows(AccessDeniedException.class, () -> customerService.updateCustomer(5L, newName("Otro")));
        verify(customerRepository, never()).save(any());
        verifyNoInteractions(auditFacade);
    }

    @Test
    void updateCustomer_SellerCannotEditALegacyCustomerWithoutCreator() {
        loginAs("ana", ANA, "SELLER");
        when(customerRepository.findById(5L)).thenReturn(Optional.of(customerCreatedBy(5L, null, null)));

        assertThrows(AccessDeniedException.class, () -> customerService.updateCustomer(5L, newName("Otro")));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateCustomer_AdministratorEditsACustomerCreatedByASeller() {
        loginAs("boss", 1L, "ADMINISTRATOR");
        when(customerRepository.findById(5L)).thenReturn(Optional.of(customerCreatedBy(5L, LUIS, "luis")));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDto result = customerService.updateCustomer(5L, newName("Nombre corregido"));

        assertEquals("luis", result.createdByUsername(), "el creador original no cambia");
        assertEquals("boss", result.updatedByUsername());
        verify(auditFacade).record(eq("CUSTOMER"), eq(5L), eq(AuditAction.UPDATE), eq(1L), eq("boss"), any());
    }

    @Test
    void updateCustomer_WithoutRealChanges_IsNotAudited() {
        loginAs("ana", ANA, "SELLER");
        Customer own = customerCreatedBy(5L, ANA, "ana");
        own.setUpdatedByUsername("ana");
        when(customerRepository.findById(5L)).thenReturn(Optional.of(own));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        customerService.updateCustomer(5L, newName("Maria Lopez"));

        verifyNoInteractions(auditFacade);
    }

    @Test
    void updateCustomer_GeneralCustomer_ThrowsException() {
        Customer general = new Customer();
        general.setId(1L);
        general.setStoreId(STORE_ID);
        general.setFullName("Cliente General");
        general.setIdentification("9999999999");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(general));

        UpdateCustomerCommand command = new UpdateCustomerCommand("Nuevo Nombre", "a@a.com", "123", "Calle 2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> customerService.updateCustomer(1L, command));
        assertTrue(ex.getMessage().contains("Cliente General"));
    }

    @Test
    void deleteCustomer_AuditsTheDeactivation() {
        loginAs("boss", 1L, "ADMINISTRATOR");
        Customer customer = customerCreatedBy(5L, ANA, "ana");
        when(customerRepository.findById(5L)).thenReturn(Optional.of(customer));

        customerService.deleteCustomer(5L);

        assertFalse(customer.isActive());
        verify(auditFacade).record(eq("CUSTOMER"), eq(5L), eq(AuditAction.DELETE), eq(1L), eq("boss"),
                eq(Map.of("active", new FieldChange(true, false))));
    }

    @Test
    void deleteCustomer_RefusedByAValidator_KeepsTheCustomerActive() {
        loginAs("boss", 1L, "ADMINISTRATOR");
        Customer customer = customerCreatedBy(5L, ANA, "ana");
        when(customerRepository.findById(5L)).thenReturn(Optional.of(customer));
        doThrow(new IllegalStateException("tiene deuda de fiado pendiente")).when(deleteValidator).validateBeforeDelete(5L);

        assertThrows(IllegalStateException.class, () -> customerService.deleteCustomer(5L));

        assertTrue(customer.isActive());
        verify(customerRepository, never()).save(any());
        verifyNoInteractions(auditFacade);
    }

    @Test
    void deleteCustomer_GeneralCustomer_ThrowsException() {
        Customer general = new Customer();
        general.setId(1L);
        general.setStoreId(STORE_ID);
        general.setFullName("Cliente General");
        general.setIdentification("9999999999");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(general));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> customerService.deleteCustomer(1L));
        assertTrue(ex.getMessage().contains("Cliente General"));
    }

    @Test
    void searchCustomers_MarksEditableOnlyTheSellersOwnCustomers() {
        loginAs("ana", ANA, "SELLER");
        Customer mine = customerCreatedBy(5L, ANA, "ana");
        Customer theirs = customerCreatedBy(6L, LUIS, "luis");
        Customer legacy = customerCreatedBy(7L, null, null);
        Customer general = customerCreatedBy(1L, ANA, "ana");
        general.setIdentification("9999999999");
        when(customerRepository.findAllByStoreId(eq(STORE_ID), any()))
                .thenReturn(new PageImpl<>(List.of(mine, theirs, legacy, general)));

        List<CustomerDto> page = customerService.searchCustomers(null, PageRequest.of(0, 20)).getContent();

        assertEquals(List.of(true, false, false, false), page.stream().map(CustomerDto::editable).toList());
    }

    @Test
    void searchCustomers_AdministratorCanEditEveryoneButTheGeneralCustomer() {
        loginAs("boss", 1L, "ADMINISTRATOR");
        Customer theirs = customerCreatedBy(6L, LUIS, "luis");
        Customer legacy = customerCreatedBy(7L, null, null);
        when(customerRepository.findAllByStoreId(eq(STORE_ID), any()))
                .thenReturn(new PageImpl<>(List.of(theirs, legacy)));

        List<CustomerDto> page = customerService.searchCustomers(null, PageRequest.of(0, 20)).getContent();

        assertTrue(page.stream().allMatch(CustomerDto::editable));
    }
}
