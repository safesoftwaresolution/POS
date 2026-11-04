package com.sciencebot.pos.billing.internal.mappers;

import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.credit.CreditFacade;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleItemDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class BillingCanonicalMapper {

    private final CustomerFacade customerFacade;
    private final ProductFacade productFacade;

    private static final String CREDIT_PAYMENT_METHOD = "CREDIT";

    private final CreditFacade creditFacade;

    public BillingCanonicalMapper(
            @Lazy CustomerFacade customerFacade,
            @Lazy ProductFacade productFacade,
            @Lazy CreditFacade creditFacade
    ) {
        this.customerFacade = customerFacade;
        this.productFacade = productFacade;
        this.creditFacade = creditFacade;
    }

    public InvoiceRequest toInvoiceRequest(SaleDto sale) {
        if (sale == null) {
            return null;
        }

        CustomerBillingData customerData = resolveCustomer(sale.customerId());
        List<InvoiceItemData> items = mapItems(sale.items());
        String paymentMethodCode = resolveDianPaymentMethod(sale.paymentMethod());

        // Una venta a fiado se factura a crédito (2) con su fecha de vencimiento; el resto, de contado (1).
        boolean onCredit = sale.paymentMethod() != null
                && CREDIT_PAYMENT_METHOD.equalsIgnoreCase(sale.paymentMethod().trim());
        LocalDate dueDate = onCredit
                ? creditFacade.findChargeDueDate(sale.storeId(), sale.id()).orElse(LocalDate.now().plusDays(30))
                : null;

        // El rango de numeración lo decide el adaptador del proveedor con la configuración del local.
        return new InvoiceRequest(
                sale.id(),
                sale.storeId(),
                null,
                sale.invoiceNumber(),
                onCredit ? "2" : "1", // 1 = Contado, 2 = Crédito (Estándar DIAN)
                paymentMethodCode,
                customerData,
                items,
                sale.totalAmount(),
                "Factura emitida desde Sistema POS",
                dueDate
        );
    }

    private CustomerBillingData resolveCustomer(Long customerId) {
        if (customerId != null) {
            Optional<CustomerDto> customerOpt = customerFacade.getById(customerId);
            if (customerOpt.isPresent()) {
                CustomerDto customer = customerOpt.get();
                String rawId = customer.identification() != null ? customer.identification().trim() : "";

                // Si no es el consumidor final genérico
                if (!"222222222222".equals(rawId) && !"Cliente General".equalsIgnoreCase(customer.fullName())) {
                    String idClean = rawId;
                    String dv = customer.dv() != null && !customer.dv().isBlank() ? customer.dv().trim() : "";
                    if (rawId.contains("-")) {
                        String[] parts = rawId.split("-");
                        idClean = parts[0].trim().replaceAll("[^0-9a-zA-Z]", "");
                        if (dv.isBlank() && parts.length > 1) {
                            dv = parts[1].trim();
                        }
                    } else {
                        idClean = rawId.replaceAll("[^0-9a-zA-Z]", "");
                    }

                    boolean isJuridica = (customer.legalOrganizationId() != null && customer.legalOrganizationId() == 1)
                            || idClean.length() == 9
                            || !dv.isBlank();

                    String docCode = isJuridica ? "31" : "13"; // 31 = NIT, 13 = Cédula de ciudadanía
                    String orgCode = isJuridica ? "1" : "2";   // 1 = Jurídica, 2 = Natural
                    String company = isJuridica ? customer.fullName() : null;
                    String names = !isJuridica ? customer.fullName() : null;

                    String tributeCode = (customer.tributeId() != null && customer.tributeId() == 1) ? "01" : "ZZ";
                    List<String> responsibilities = isJuridica ? List.of("O-13", "R-99-PN") : List.of("R-99-PN");

                    String munCode = customer.municipalityId() != null
                            ? String.valueOf(customer.municipalityId())
                            : "11001";

                    return new CustomerBillingData(
                            docCode,
                            idClean,
                            dv,
                            orgCode,
                            tributeCode,
                            responsibilities,
                            names,
                            company,
                            null,
                            (customer.email() != null && !customer.email().isBlank()) ? customer.email().trim() : "cliente@tienda.com",
                            customer.phone(),
                            customer.address(),
                            "CO",
                            munCode
                    );
                }
            }
        }

        // Consumidor Final genérico estándar DIAN
        return new CustomerBillingData(
                "13",
                "222222222222",
                "",
                "2",
                "ZZ",
                List.of("R-99-PN"),
                "Consumidor Final",
                null,
                null,
                "consumidorfinal@tienda.com",
                null,
                null,
                "CO",
                "11001"
        );
    }

    private String resolveDianPaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "10"; // Default: Efectivo
        }
        return switch (paymentMethod.trim().toUpperCase()) {
            case "TRANSFER" -> "31";
            case "NEQUI", "DAVIPLATA" -> "42";
            case "CARD" -> "48";
            case "CREDIT" -> "1"; // Instrumento no definido: aún no se ha pagado
            default -> "10";
        };
    }

    private List<InvoiceItemData> mapItems(List<SaleItemDto> items) {
        List<InvoiceItemData> list = new ArrayList<>();
        if (items != null) {
            for (SaleItemDto item : items) {
                String cleanName = item.productName() != null ? item.productName().replaceAll("[^a-zA-Z0-9]", "") : "PROD";
                String productCode = "REF-" + cleanName;

                BigDecimal taxRate = item.taxRate() != null ? item.taxRate() : BigDecimal.ZERO;
                BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                BigDecimal netUnitPrice = item.unitPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);
                BigDecimal netSubtotal = netUnitPrice.multiply(BigDecimal.valueOf(item.quantity()));

                String standardCode = "999";
                String unitMeasureCode = "94";  // 94 = Unidad estándar DIAN
                if (item.unitMeasureCode() != null && !item.unitMeasureCode().isBlank()) {
                    unitMeasureCode = item.unitMeasureCode().trim();
                }
                if (item.unspscCode() != null && !item.unspscCode().isBlank()) {
                    // La venta guardó el código con que se facturó (servicios): no se consulta el catálogo.
                    standardCode = item.unspscCode().trim();
                } else if (item.productId() != null && productFacade != null) {
                    try {
                        Optional<ProductDto> prodOpt = productFacade.getById(item.productId());
                        if (prodOpt.isPresent() && prodOpt.get().unspscCode() != null && !prodOpt.get().unspscCode().isBlank()) {
                            standardCode = prodOpt.get().unspscCode().trim();
                        }
                    } catch (Exception e) {
                        // fallback a "999"
                    }
                }

                list.add(new InvoiceItemData(
                        productCode,
                        item.productName(),
                        BigDecimal.valueOf(item.quantity()),
                        netUnitPrice,
                        taxRate,
                        netSubtotal,
                        unitMeasureCode,
                        standardCode,
                        "01",  // 01 = IVA estándar DIAN
                        BigDecimal.ZERO,
                        taxRate.compareTo(BigDecimal.ZERO) == 0
                ));
            }
        }
        return list;
    }
}
