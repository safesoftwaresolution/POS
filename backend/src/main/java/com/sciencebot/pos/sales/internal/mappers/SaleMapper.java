package com.sciencebot.pos.sales.internal.mappers;

import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleItemDto;
import com.sciencebot.pos.sales.internal.entities.Sale;
import com.sciencebot.pos.sales.internal.entities.SaleItem;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.users.UserFacade;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SaleMapper {

    private final ProductFacade productFacade;
    private final CustomerFacade customerFacade;
    private final UserFacade userFacade;

    public SaleMapper(
            @Lazy ProductFacade productFacade,
            @Lazy CustomerFacade customerFacade,
            @Lazy UserFacade userFacade) {
        this.productFacade = productFacade;
        this.customerFacade = customerFacade;
        this.userFacade = userFacade;
    }

    public SaleDto toDto(Sale sale) {
        if (sale == null) {
            return null;
        }

        String customerName = customerFacade.getById(sale.getCustomerId())
                .map(c -> c.fullName())
                .orElse("Cliente Desconocido");

        String username = "Desconocido";
        try {
            username = userFacade.getById(sale.getUserId()).username();
        } catch (Exception e) {
            // Fallback
        }

        List<SaleItemDto> itemDtos = sale.getItems().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new SaleDto(
                sale.getId(),
                sale.getStoreId(),
                sale.getCustomerId(),
                sale.getInvoiceNumber(),
                sale.getCreatedAt(),
                customerName,
                sale.getTotalAmount(),
                sale.getCashReceived(),
                sale.getCashChange(),
                username,
                itemDtos,
                sale.getPaymentMethod()
        );
    }

    public SaleItemDto toDto(SaleItem item) {
        if (item == null) {
            return null;
        }

        String productName = item.getItemName() != null ? item.getItemName() : lookupName(item);

        return new SaleItemDto(
                item.getId(),
                item.getProductId(),
                productName,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal(),
                item.getTaxRate() != null ? item.getTaxRate() : java.math.BigDecimal.ZERO,
                item.getTaxAmount() != null ? item.getTaxAmount() : java.math.BigDecimal.ZERO,
                item.getItemType(),
                item.getServiceId(),
                item.getUnitMeasureCode(),
                item.getUnspscCode()
        );
    }

    /** Ventas anteriores a los servicios no guardaron el nombre: se resuelve desde el producto. */
    private String lookupName(SaleItem item) {
        if (item.getProductId() == null) {
            return "Servicio Desconocido";
        }
        return productFacade.getById(item.getProductId())
                .map(p -> p.name())
                .orElse("Producto Desconocido");
    }
}
