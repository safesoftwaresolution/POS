package com.sciencebot.pos.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Solicitud para crear y validar una Nota Débito ante Factus y la DIAN")
public record CreateDebitNoteRequest(
        @Schema(description = "Código único de referencia de la nota débito para garantizar idempotencia", example = "ND-2026-0001")
        @NotBlank(message = "El reference_code es obligatorio")
        @JsonProperty("reference_code")
        String referenceCode,

        @Schema(description = "Código del concepto de corrección (ej. 1=Intereses, 2=Gastos por cobrar, 3=Cambio del valor, 4=Otros)", example = "3")
        @NotBlank(message = "El código de concepto de corrección (correction_concept_code) es obligatorio")
        @JsonProperty("correction_concept_code")
        String correctionConceptCode,

        @Schema(description = "Tipo de operación. Solo se soporta 30 (con referencia a factura electrónica), que es el valor por defecto", example = "30")
        @JsonProperty("customization_id")
        String customizationId,

        @Schema(description = "Número legal de la factura electrónica a la que se le aplica la nota débito", example = "SETP990002519")
        @JsonProperty("bill_number")
        String billNumber,

        @Schema(description = "ID del rango de numeración en Factus (opcional si solo hay un rango activo)", example = "1776")
        @JsonProperty("numbering_range_id")
        Integer numberingRangeId,

        @Schema(description = "Observaciones o motivo de la nota débito (máx. 500 caracteres)", example = "Cobro de intereses moratorios por pago extemporáneo")
        String observation,

        @Schema(description = "Detalles de los medios de pago aplicados al débito")
        @JsonProperty("payment_details")
        List<@Valid PaymentDetailRequest> paymentDetails,

        @Schema(description = "Información fiscal del cliente")
        @Valid
        CustomerDebitNoteRequest customer,

        @Schema(description = "Ítems o conceptos cobrados o ajustados en la nota débito")
        @NotEmpty(message = "La nota débito debe contener al menos un ítem")
        List<@Valid ItemDebitNoteRequest> items
) {
    public CreateDebitNoteRequest {
        if (customizationId == null || customizationId.isBlank()) {
            customizationId = "30";
        }
    }

    @Schema(description = "Detalle de medio de pago de la nota débito")
    public record PaymentDetailRequest(
            @Schema(description = "Forma de pago: 1=Contado, 2=Crédito", example = "1")
            @JsonProperty("payment_form")
            String paymentForm,

            @Schema(description = "Método de pago (ej. 10=Efectivo, 42=Transferencia bancaria/Nequi, 48=Tarjeta Crédito)", example = "10")
            @JsonProperty("payment_method_code")
            String paymentMethodCode,

            @Schema(description = "Código de referencia del pago", example = "pago-nd-001")
            @JsonProperty("reference_code")
            String referenceCode,

            @Schema(description = "Fecha de vencimiento (yyyy-MM-dd). Obligatoria cuando payment_form=2 (Crédito)", example = "2026-11-30")
            @JsonProperty("due_date")
            String dueDate,

            @Schema(description = "Monto pagado", example = "17850.00")
            String amount
    ) {}

    @Schema(description = "Datos fiscales del adquiriente/cliente para la nota débito")
    public record CustomerDebitNoteRequest(
        
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

    @Schema(description = "Ítem o concepto cobrado o ajustado en la nota débito")
    public record ItemDebitNoteRequest(
            @Schema(description = "Código de referencia del producto o concepto", example = "INT-MORA")
            @JsonProperty("code_reference")
            String codeReference,

            @Schema(description = "Nombre descriptivo del producto o concepto", example = "Intereses por mora")
            String name,

            @Schema(description = "Cantidad debitada", example = "1.00")
            String quantity,

            @Schema(description = "Porcentaje de descuento aplicado", example = "0.00")
            @JsonProperty("discount_rate")
            String discountRate,

            @Schema(description = "Precio unitario sin impuestos", example = "15000.00")
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