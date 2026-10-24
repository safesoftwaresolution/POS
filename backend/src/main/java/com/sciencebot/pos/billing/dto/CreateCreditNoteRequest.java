package com.sciencebot.pos.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Solicitud para crear y validar una Nota Crédito ante Factus y la DIAN")
public record CreateCreditNoteRequest(
        @Schema(description = "Código único de referencia de la nota crédito para garantizar idempotencia", example = "NOTA-2026-0015")
        @NotBlank(message = "El reference_code es obligatorio")
        @JsonProperty("reference_code")
        String referenceCode,

        @Schema(description = "Código del concepto de corrección (ej. 1=Devolución parcial, 2=Anulación de factura)", example = "2")
        @NotBlank(message = "El código de concepto de corrección (correction_concept_code) es obligatorio")
        @JsonProperty("correction_concept_code")
        String correctionConceptCode,

        @Schema(description = "Tipo de operación (20=Con referencia a factura, 22=Sin referencia). Por defecto 20", example = "20")
        @JsonProperty("customization_id")
        String customizationId,

        @Schema(description = "Número legal de la factura electrónica a la que se le aplica la nota crédito", example = "SETP990002519")
        @JsonProperty("bill_number")
        String billNumber,

        @Schema(description = "ID del rango de numeración en Factus (opcional si solo hay un rango activo)", example = "1776")
        @JsonProperty("numbering_range_id")
        Integer numberingRangeId,

        @Schema(description = "Observaciones o motivo de la nota crédito (máx. 500 caracteres)", example = "Anulación de factura por devolución de mercancía")
        String observation,

        @Schema(description = "Detalles de los medios de pago utilizados para la devolución")
        @JsonProperty("payment_details")
        List<@Valid PaymentDetailRequest> paymentDetails,

        @Schema(description = "Información fiscal del cliente")
        @Valid
        CustomerCreditNoteRequest customer,

        @Schema(description = "Ítems o productos afectados por la nota crédito")
        @NotEmpty(message = "La nota crédito debe contener al menos un ítem")
        List<@Valid ItemCreditNoteRequest> items
) {
    public CreateCreditNoteRequest {
        if (customizationId == null || customizationId.isBlank()) {
            customizationId = "20";
        }
    }

    @Schema(description = "Detalle de medio de pago de la nota crédito")
    public record PaymentDetailRequest(
            @Schema(description = "Forma de pago: 1=Contado, 2=Crédito", example = "1")
            @JsonProperty("payment_form")
            String paymentForm,

            @Schema(description = "Método de pago (ej. 10=Efectivo, 42=Transferencia bancaria/Nequi, 48=Tarjeta Crédito)", example = "42")
            @JsonProperty("payment_method_code")
            String paymentMethodCode,

            @Schema(description = "Código de referencia del pago", example = "pago-001")
            @JsonProperty("reference_code")
            String referenceCode,

            @Schema(description = "Monto pagado", example = "83300.00")
            String amount
    ) {}

    @Schema(description = "Datos fiscales del adquiriente/cliente para la nota crédito")
    public record CustomerCreditNoteRequest(
            @Schema(description = "Tipo de documento (13=Cédula, 31=NIT)", example = "31")
            @JsonProperty("identification_document_code")
            String identificationDocumentCode,

            @Schema(description = "Número de identificación fiscal sin guiones", example = "123456789")
            String identification,

            @Schema(description = "Dígito de verificación (solo para NIT)", example = "4")
            String dv,

            @Schema(description = "Razón social (para personas jurídicas)", example = "Alan company name")
            String company,

            @Schema(description = "Nombre comercial", example = "Alan trade name")
            @JsonProperty("trade_name")
            String tradeName,

            @Schema(description = "Nombres y apellidos (para personas naturales)", example = "Alan Turing")
            String names,

            @Schema(description = "Dirección", example = "calle 1 # 1-1")
            String address,

            @Schema(description = "Correo electrónico para notificación fiscal", example = "alan.company@email.com")
            String email,

            @Schema(description = "Teléfono de contacto", example = "1234567890")
            String phone,

            @Schema(description = "Tipo de organización: 1=Jurídica, 2=Natural", example = "1")
            @JsonProperty("legal_organization_code")
            String legalOrganizationCode,

            @Schema(description = "Código de tributo: 01=IVA, ZZ=No aplica", example = "ZZ")
            @JsonProperty("tribute_code")
            String tributeCode,

            @Schema(description = "Código de país (ISO)", example = "CO")
            @JsonProperty("country_code")
            String countryCode,

            @Schema(description = "Responsabilidades fiscales (ej. R-99-PN, O-13)", example = "[\"R-99-PN\"]")
            List<String> responsibilities,

            @Schema(description = "Código DANE del municipio", example = "68679")
            @JsonProperty("municipality_code")
            String municipalityCode
    ) {}

    @Schema(description = "Ítem o producto en la nota crédito")
    public record ItemCreditNoteRequest(
            @Schema(description = "Código de referencia del producto", example = "PROD-000A")
            @JsonProperty("code_reference")
            String codeReference,

            @Schema(description = "Nombre descriptivo del producto o servicio", example = "Producto A")
            String name,

            @Schema(description = "Cantidad devuelta o acreditada", example = "1.00")
            String quantity,

            @Schema(description = "Porcentaje de descuento aplicado", example = "0.00")
            @JsonProperty("discount_rate")
            String discountRate,

            @Schema(description = "Precio unitario sin impuestos", example = "10000.00")
            String price,

            @Schema(description = "Unidad de medida estándar DIAN (ej. 94=Unidad)", example = "94")
            @JsonProperty("unit_measure_code")
            String unitMeasureCode,

            @Schema(description = "Estándar de identificación del producto (ej. 999=Estándar de adopción del contribuyente)", example = "999")
            @JsonProperty("standard_code")
            String standardCode,

            @Schema(description = "Impuestos aplicados al ítem")
            List<TaxItemRequest> taxes
    ) {
        @Schema(description = "Impuesto de un ítem")
        public record TaxItemRequest(
                @Schema(description = "Código de impuesto: 01=IVA, 04=INC", example = "01")
                String code,

                @Schema(description = "Porcentaje o tarifa de impuesto", example = "19.00")
                String rate
        ) {}
    }
}
