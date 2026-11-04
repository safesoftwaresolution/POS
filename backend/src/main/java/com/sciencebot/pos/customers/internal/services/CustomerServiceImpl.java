package com.sciencebot.pos.customers.internal.services;

import com.sciencebot.pos.audit.AuditAction;
import com.sciencebot.pos.audit.AuditFacade;
import com.sciencebot.pos.audit.FieldChange;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CreateCustomerCommand;
import com.sciencebot.pos.customers.CustomerDeleteValidator;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.customers.UpdateCustomerCommand;
import com.sciencebot.pos.customers.internal.entities.Customer;
import com.sciencebot.pos.customers.internal.mappers.CustomerMapper;
import com.sciencebot.pos.customers.internal.repositories.CustomerRepository;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerFacade {

    private static final String GENERAL_CUSTOMER_IDENTIFICATION = "9999999999";
    private static final String AUDIT_ENTITY = "CUSTOMER";

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final UserFacade userFacade;
    private final AuditFacade auditFacade;
    private final List<CustomerDeleteValidator> deleteValidators;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerMapper customerMapper,
                               UserFacade userFacade, AuditFacade auditFacade,
                               List<CustomerDeleteValidator> deleteValidators) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.userFacade = userFacade;
        this.auditFacade = auditFacade;
        this.deleteValidators = deleteValidators;
    }

    /** Quién hace la solicitud: id y nombre para auditar, y si es vendedor (solo edita lo que creó). */
    private record Actor(Long userId, String username, boolean seller) {
        boolean canEdit(Customer customer) {
            return !seller || (customer.getCreatedBy() != null && customer.getCreatedBy().equals(userId));
        }
    }

    private Actor currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return new Actor(null, null, false);
        }
        boolean seller = auth.getAuthorities().stream().anyMatch(a -> "ROLE_SELLER".equals(a.getAuthority()));
        Long userId = userFacade.findByUsername(auth.getName()).map(u -> u.id()).orElse(null);
        return new Actor(userId, auth.getName(), seller);
    }

    @Override
    public Optional<CustomerDto> getById(Long id) {
        Actor actor = currentActor();
        return customerRepository.findById(id)
                .filter(c -> c.getStoreId().equals(TenantContext.getStoreId()))
                .map(c -> toDto(c, actor));
    }

    @Override
    public Optional<CustomerDto> getByIdentification(String identification) {
        Actor actor = currentActor();
        return customerRepository.findByStoreIdAndIdentification(requireCurrentStoreId(), identification)
                .map(c -> toDto(c, actor));
    }

    @Override
    @Transactional
    public CustomerDto createCustomer(CreateCustomerCommand command) {
        if (command.fullName() == null || command.fullName().isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        }
        if (command.identification() == null || command.identification().isBlank()) {
            throw new IllegalArgumentException("La identificación del cliente es obligatoria");
        }
        Long storeId = requireCurrentStoreId();
        String cleanIdentification = command.identification().trim();
        if (customerRepository.existsByStoreIdAndIdentification(storeId, cleanIdentification)) {
            throw new IllegalArgumentException("Ya existe un cliente con la identificación: " + cleanIdentification);
        }

        Actor actor = currentActor();
        Customer customer = new Customer();
        customer.setStoreId(storeId);
        customer.setFullName(command.fullName().trim());
        customer.setIdentification(cleanIdentification);
        customer.setEmail(command.email() != null ? command.email().trim() : null);
        customer.setPhone(command.phone() != null ? command.phone().trim() : null);
        customer.setAddress(command.address() != null ? command.address().trim() : null);
        customer.setDv(command.dv() != null ? command.dv().trim() : null);
        customer.setLegalOrganizationId(command.legalOrganizationId() != null ? command.legalOrganizationId() : 13);
        customer.setTributeId(command.tributeId() != null ? command.tributeId() : 21);
        customer.setMunicipalityId(command.municipalityId());
        customer.setActive(true);
        customer.setCreatedBy(actor.userId());
        customer.setCreatedByUsername(actor.username());
        customer.setUpdatedBy(actor.userId());
        customer.setUpdatedByUsername(actor.username());

        Customer saved = customerRepository.save(customer);
        auditFacade.record(AUDIT_ENTITY, saved.getId(), AuditAction.CREATE, actor.userId(), actor.username(),
                diff(blank(), saved));
        return toDto(saved, actor);
    }

    @Override
    @Transactional
    public CustomerDto updateCustomer(Long id, UpdateCustomerCommand command) {
        Customer customer = customerRepository.findById(id)
                .filter(c -> c.getStoreId().equals(TenantContext.getStoreId()))
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + id));

        checkNotGeneralCustomer(customer);
        Actor actor = currentActor();
        if (!actor.canEdit(customer)) {
            throw new AccessDeniedException("Solo puedes editar los clientes que tú creaste");
        }
        if (command.fullName() == null || command.fullName().isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        }

        Customer before = snapshot(customer);
        customer.setFullName(command.fullName().trim());
        customer.setEmail(command.email() != null ? command.email().trim() : null);
        customer.setPhone(command.phone() != null ? command.phone().trim() : null);
        customer.setAddress(command.address() != null ? command.address().trim() : null);
        if (command.dv() != null) customer.setDv(command.dv().trim());
        if (command.legalOrganizationId() != null) customer.setLegalOrganizationId(command.legalOrganizationId());
        if (command.tributeId() != null) customer.setTributeId(command.tributeId());
        if (command.municipalityId() != null) customer.setMunicipalityId(command.municipalityId());

        Map<String, FieldChange> changes = diff(before, customer);
        // Guardar sin cambios reales no es una edición: no se registra ni cambia "editado por".
        if (!changes.isEmpty()) {
            customer.setUpdatedBy(actor.userId());
            customer.setUpdatedByUsername(actor.username());
        }
        Customer saved = customerRepository.save(customer);
        if (!changes.isEmpty()) {
            auditFacade.record(AUDIT_ENTITY, saved.getId(), AuditAction.UPDATE, actor.userId(), actor.username(), changes);
        }
        return toDto(saved, actor);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .filter(c -> c.getStoreId().equals(TenantContext.getStoreId()))
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + id));

        checkNotGeneralCustomer(customer);
        for (CustomerDeleteValidator validator : deleteValidators) {
            validator.validateBeforeDelete(id);
        }
        Actor actor = currentActor();
        customer.setActive(false);
        customer.setUpdatedBy(actor.userId());
        customer.setUpdatedByUsername(actor.username());
        customerRepository.save(customer);
        auditFacade.record(AUDIT_ENTITY, customer.getId(), AuditAction.DELETE, actor.userId(), actor.username(),
                Map.of("active", new FieldChange(true, false)));
    }

    @Override
    public Page<CustomerDto> searchCustomers(String search, Pageable pageable) {
        Long storeId = requireCurrentStoreId();
        Actor actor = currentActor();
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();
        if (cleanSearch == null) {
            return customerRepository.findAllByStoreId(storeId, pageable).map(c -> toDto(c, actor));
        }
        return customerRepository.searchCustomers(storeId, cleanSearch, pageable).map(c -> toDto(c, actor));
    }

    private CustomerDto toDto(Customer customer, Actor actor) {
        boolean editable = actor.canEdit(customer) && !GENERAL_CUSTOMER_IDENTIFICATION.equals(customer.getIdentification());
        return customerMapper.toDto(customer, editable);
    }

    /** Un cliente sin valores (ni los predeterminados de la entidad): punto de partida para registrar una alta completa. */
    private static Customer blank() {
        Customer empty = new Customer();
        empty.setLegalOrganizationId(null);
        empty.setTributeId(null);
        return empty;
    }

    /** Copia solo los campos auditados, para comparar antes y después de editar. */
    private static Customer snapshot(Customer customer) {
        Customer copy = new Customer();
        copy.setFullName(customer.getFullName());
        copy.setEmail(customer.getEmail());
        copy.setPhone(customer.getPhone());
        copy.setAddress(customer.getAddress());
        copy.setDv(customer.getDv());
        copy.setLegalOrganizationId(customer.getLegalOrganizationId());
        copy.setTributeId(customer.getTributeId());
        copy.setMunicipalityId(customer.getMunicipalityId());
        copy.setIdentification(customer.getIdentification());
        return copy;
    }

    /** Campos auditados que cambiaron entre {@code before} y {@code after}. */
    private static Map<String, FieldChange> diff(Customer before, Customer after) {
        Map<String, FieldChange> changes = new LinkedHashMap<>();
        track(changes, "fullName", before.getFullName(), after.getFullName());
        track(changes, "identification", before.getIdentification(), after.getIdentification());
        track(changes, "email", before.getEmail(), after.getEmail());
        track(changes, "phone", before.getPhone(), after.getPhone());
        track(changes, "address", before.getAddress(), after.getAddress());
        track(changes, "dv", before.getDv(), after.getDv());
        track(changes, "legalOrganizationId", before.getLegalOrganizationId(), after.getLegalOrganizationId());
        track(changes, "tributeId", before.getTributeId(), after.getTributeId());
        track(changes, "municipalityId", before.getMunicipalityId(), after.getMunicipalityId());
        return changes;
    }

    private static void track(Map<String, FieldChange> changes, String field, Object from, Object to) {
        if (!Objects.equals(from, to)) {
            changes.put(field, new FieldChange(from, to));
        }
    }

    private void checkNotGeneralCustomer(Customer customer) {
        if (GENERAL_CUSTOMER_IDENTIFICATION.equals(customer.getIdentification())) {
            throw new IllegalArgumentException("No se puede editar ni eliminar el Cliente General");
        }
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
