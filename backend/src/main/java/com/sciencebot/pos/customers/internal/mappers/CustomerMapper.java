package com.sciencebot.pos.customers.internal.mappers;

import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.internal.entities.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerDto toDto(Customer customer) {
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
                customer.getMunicipalityId()
        );
    }
}
