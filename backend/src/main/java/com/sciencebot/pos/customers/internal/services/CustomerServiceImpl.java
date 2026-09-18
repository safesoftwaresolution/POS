package com.sciencebot.pos.customers.internal.services;

import com.sciencebot.pos.config.TenantContext;
import com.sciencebot.pos.customers.*;
import com.sciencebot.pos.customers.internal.entities.Customer;
import com.sciencebot.pos.customers.internal.repositories.CustomerRepository;
import com.sciencebot.pos.customers.internal.mappers.CustomerMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerFacade {

    private static final String GENERAL_CUSTOMER_IDENTIFICATION = "9999999999";

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    public Optional<CustomerDto> getById(Long id) {
        return customerRepository.findById(id)
                .filter(c -> c.getStoreId().equals(TenantContext.getStoreId()))
                .map(customerMapper::toDto);
    }

    @Override
    public Optional<CustomerDto> getByIdentification(String identification) {
        return customerRepository.findByStoreIdAndIdentification(requireCurrentStoreId(), identification)
                .map(customerMapper::toDto);
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

        Customer saved = customerRepository.save(customer);
        return customerMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CustomerDto updateCustomer(Long id, UpdateCustomerCommand command) {
        Customer customer = customerRepository.findById(id)
                .filter(c -> c.getStoreId().equals(TenantContext.getStoreId()))
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + id));

        checkNotGeneralCustomer(customer);

        if (command.fullName() == null || command.fullName().isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        }

        customer.setFullName(command.fullName().trim());
        customer.setEmail(command.email() != null ? command.email().trim() : null);
        customer.setPhone(command.phone() != null ? command.phone().trim() : null);
        customer.setAddress(command.address() != null ? command.address().trim() : null);
        if (command.dv() != null) customer.setDv(command.dv().trim());
        if (command.legalOrganizationId() != null) customer.setLegalOrganizationId(command.legalOrganizationId());
        if (command.tributeId() != null) customer.setTributeId(command.tributeId());
        if (command.municipalityId() != null) customer.setMunicipalityId(command.municipalityId());

        Customer saved = customerRepository.save(customer);
        return customerMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .filter(c -> c.getStoreId().equals(TenantContext.getStoreId()))
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + id));

        checkNotGeneralCustomer(customer);

        customer.setActive(false);
        customerRepository.save(customer);
    }

    @Override
    public Page<CustomerDto> searchCustomers(String search, Pageable pageable) {
        Long storeId = requireCurrentStoreId();
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();
        if (cleanSearch == null) {
            return customerRepository.findAllByStoreId(storeId, pageable).map(customerMapper::toDto);
        }
        return customerRepository.searchCustomers(storeId, cleanSearch, pageable).map(customerMapper::toDto);
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
