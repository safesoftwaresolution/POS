package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class BillingCanonicalMapperTest {

    @Mock
    private CustomerFacade customerFacade;

    @Mock
    private ProductFacade productFacade;


    private BillingCanonicalMapper mapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new BillingCanonicalMapper(customerFacade, productFacade);
    }

    @Test
    void toInvoiceRequest_WithNaturalPersonCustomer_ResolvesNaturalPersonCorrectly() {
        Long customerId = 15L;
        CustomerDto customer = new CustomerDto(
                customerId,
                "Carlos Gómez",
                "1020304050",
                "carlos@example.com",
                "3001234567",
                "Calle 100 # 20-30",
                true,
                "",
                2, // Persona Natural
                21, // No responsable de IVA
                11001
        );
        when(customerFacade.getById(customerId)).thenReturn(Optional.of(customer));

        SaleItemDto item = new SaleItemDto(1L, 100L, "Camiseta Polo", 2, BigDecimal.valueOf(50000.00), BigDecimal.valueOf(100000.00), BigDecimal.valueOf(19.00), BigDecimal.valueOf(15966.38));
        SaleDto sale = new SaleDto(
                1L, 5L, customerId, "FACT-0001", LocalDateTime.now(), "Carlos Gómez",
                BigDecimal.valueOf(100000.00), BigDecimal.valueOf(100000.00), BigDecimal.ZERO,
                "carlos.admin", List.of(item), "CARD"
        );

        InvoiceRequest request = mapper.toInvoiceRequest(sale);

        assertNotNull(request);
        assertEquals(5L, request.storeId());
        assertNull(request.numberingRangeId(), "el rango lo decide el adaptador con la configuración del local");
        assertEquals("48", request.paymentMethodCode()); // CARD -> 48

        CustomerBillingData custData = request.customer();
        assertNotNull(custData);
        assertEquals("13", custData.identificationDocumentCode()); // Persona Natural -> 13
        assertEquals("1020304050", custData.identification());
        assertEquals("2", custData.legalOrganizationCode()); // Natural
        assertEquals("Carlos Gómez", custData.names());
        assertNull(custData.company());
        assertEquals("carlos@example.com", custData.email());
        assertEquals("11001", custData.municipalityCode());

        // Check item taxes
        assertEquals(1, request.items().size());
        InvoiceItemData itemData = request.items().get(0);
        assertEquals(BigDecimal.valueOf(19.0), itemData.taxRate());
        assertFalse(itemData.isExcluded());
        assertEquals("01", itemData.taxCode());
    }

    @Test
    void toInvoiceRequest_WithLegalEntityCustomer_ResolvesLegalEntityCorrectly() {
        Long customerId = 20L;
        CustomerDto customer = new CustomerDto(
                customerId,
                "Inversiones Acme SAS",
                "900123456-7",
                "facturacion@acme.com",
                "6012345678",
                "Carrera 7 # 72-10",
                true,
                "7",
                1, // Jurídica
                1, // Responsable de IVA
                11001
        );
        when(customerFacade.getById(customerId)).thenReturn(Optional.of(customer));

        SaleDto sale = new SaleDto(
                2L, 5L, customerId, "FACT-0002", LocalDateTime.now(), "Inversiones Acme SAS",
                BigDecimal.valueOf(200000.00), BigDecimal.valueOf(200000.00), BigDecimal.ZERO,
                "admin", List.of(), "TRANSFER"
        );

        InvoiceRequest request = mapper.toInvoiceRequest(sale);

        assertNotNull(request);
        assertEquals("31", request.paymentMethodCode()); // TRANSFER -> 31

        CustomerBillingData custData = request.customer();
        assertEquals("31", custData.identificationDocumentCode()); // NIT -> 31
        assertEquals("900123456", custData.identification());
        assertEquals("7", custData.dv());
        assertEquals("1", custData.legalOrganizationCode()); // Jurídica
        assertEquals("01", custData.tributeCode());
        assertEquals("Inversiones Acme SAS", custData.company());
        assertNull(custData.names());
    }

    @Test
    void toInvoiceRequest_WhenCustomerNull_DefaultsToConsumidorFinal() {
        SaleDto sale = new SaleDto(
                3L, 5L, null, "FACT-0003", LocalDateTime.now(), null,
                BigDecimal.valueOf(15000.00), BigDecimal.valueOf(15000.00), BigDecimal.ZERO,
                "admin", List.of(), "CASH"
        );

        InvoiceRequest request = mapper.toInvoiceRequest(sale);

        assertNotNull(request);
        assertEquals("10", request.paymentMethodCode()); // CASH -> 10
        CustomerBillingData custData = request.customer();
        assertEquals("222222222222", custData.identification());
        assertEquals("Consumidor Final", custData.names());
        assertEquals("13", custData.identificationDocumentCode());
    }

    @Test
    void toInvoiceRequest_PaymentMethodMapping_ResolvesAllMethods() {
        SaleDto saleNequi = new SaleDto(4L, 5L, null, "FACT-0004", LocalDateTime.now(), null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(), "NEQUI");
        assertEquals("42", mapper.toInvoiceRequest(saleNequi).paymentMethodCode());

        SaleDto saleDaviplata = new SaleDto(5L, 5L, null, "FACT-0005", LocalDateTime.now(), null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(), "DAVIPLATA");
        assertEquals("42", mapper.toInvoiceRequest(saleDaviplata).paymentMethodCode());

        SaleDto saleUnknown = new SaleDto(6L, 5L, null, "FACT-0006", LocalDateTime.now(), null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(), "UNKNOWN");
        assertEquals("10", mapper.toInvoiceRequest(saleUnknown).paymentMethodCode());
    }

    @Test
    void toInvoiceRequest_ResolvesProductUnspscCode() {
        Long productId = 50L;
        ProductDto prod = new ProductDto(
                productId, "LAP-01", "770123456", "Laptop HP", "Tecnologia",
                null, null, BigDecimal.valueOf(2000000.00), BigDecimal.valueOf(2500000.00),
                5, 1, true, null,
                BigDecimal.valueOf(19.00), "43211503"
        );
        when(productFacade.getById(productId)).thenReturn(Optional.of(prod));

        SaleItemDto item = new SaleItemDto(1L, productId, "Laptop HP", 1, BigDecimal.valueOf(2500000.00), BigDecimal.valueOf(2500000.00), BigDecimal.valueOf(19.00), BigDecimal.valueOf(399159.66));
        SaleDto sale = new SaleDto(7L, 5L, null, "FACT-0007", LocalDateTime.now(), null, BigDecimal.valueOf(2500000.00), BigDecimal.valueOf(2500000.00), BigDecimal.ZERO, "admin", List.of(item), "CASH");

        InvoiceRequest request = mapper.toInvoiceRequest(sale);
        assertEquals(1, request.items().size());
        assertEquals("43211503", request.items().get(0).standardCode());
    }
}
