package com.sciencebot.pos.sales.internal.mappers;

import com.sciencebot.pos.sales.SaleReturnDto;
import com.sciencebot.pos.sales.SaleReturnItemDto;
import com.sciencebot.pos.sales.internal.entities.SaleReturn;
import com.sciencebot.pos.sales.internal.entities.SaleReturnItem;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.users.UserFacade;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SaleReturnMapper {

    private final ProductFacade productFacade;
    private final UserFacade userFacade;

    public SaleReturnMapper(@Lazy ProductFacade productFacade, @Lazy UserFacade userFacade) {
        this.productFacade = productFacade;
        this.userFacade = userFacade;
    }

    public SaleReturnDto toDto(SaleReturn saleReturn, String saleInvoiceNumber) {
        if (saleReturn == null) {
            return null;
        }

        String username = "Desconocido";
        try {
            username = userFacade.getById(saleReturn.getUserId()).username();
        } catch (Exception e) {
            // Fallback
        }

        List<SaleReturnItemDto> itemDtos = saleReturn.getItems().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new SaleReturnDto(
                saleReturn.getId(),
                saleReturn.getSaleId(),
                saleInvoiceNumber,
                saleReturn.getReason(),
                saleReturn.getTotalRefund(),
                username,
                saleReturn.getCreatedAt(),
                itemDtos
        );
    }

    private SaleReturnItemDto toDto(SaleReturnItem item) {
        String productName = productFacade.getById(item.getProductId())
                .map(p -> p.name())
                .orElse("Producto Desconocido");

        return new SaleReturnItemDto(
                item.getSaleItemId(),
                item.getProductId(),
                productName,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}
