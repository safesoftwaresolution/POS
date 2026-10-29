package com.sciencebot.pos.customers.internal.mappers;

import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.internal.entities.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    /** {@code editable}: si el usuario actual puede editar este cliente (lo decide el servicio, no el mapper). */
    public CustomerDto toDto(Customer customer, boolean editable) {
        if (customer == null) {
            return null;
        }
        return new CustomerDto(
                customer.getId(),
                customer.getFullName(),
                customer.getIdentification(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.isActive(),
                customer.getDv(),
                customer.getLegalOrganizationId(),
                customer.getTributeId(),
                customer.getMunicipalityId(),
                customer.getCreatedByUsername(),
                customer.getUpdatedByUsername(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                editable
        );
    }
}
