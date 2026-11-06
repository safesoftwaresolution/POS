package com.sciencebot.pos.billing.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sciencebot.pos.billing.internal.services.DianCheckDigit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos fiscales del local tal como figuran en su RUT. Con ellos se crea (o actualiza) su empresa en el proveedor de
 * facturación, en el entorno de producción.
 */
@Schema(description = "Datos fiscales del local (los de su RUT)")
public record FiscalProfileRequest(
        @Schema(description = "Razón social", example = "Tienda La Esquina SAS")
        @NotBlank(message = "La razón social es obligatoria")
        @Size(min = 5, max = 450, message = "La razón social debe tener entre 5 y 450 caracteres")
        String legalName,

        @Schema(description = "Nombre comercial (opcional)", example = "La Esquina")
        @Size(max = 450, message = "El nombre comercial no puede superar los 450 caracteres")
        String tradeName,

        @Schema(description = "NIT sin el dígito de verificación, solo números", example = "900123456")
        @NotBlank(message = "El NIT es obligatorio")
        @Pattern(regexp = "\\d{3,15}",
                message = "El NIT debe tener solo números (entre 3 y 15), sin puntos ni dígito de verificación")
        String nit,

        @Schema(description = "Dígito de verificación del NIT", example = "8")
        @NotBlank(message = "El dígito de verificación es obligatorio")
        @Pattern(regexp = "\\d", message = "El dígito de verificación es un solo número")
        String dv,

        @Schema(description = "Tipo de organización: 1 persona jurídica, 2 persona natural", example = "1")
        @NotNull(message = "El tipo de organización es obligatorio")
        @Min(value = 1, message = "El tipo de organización debe ser 1 (persona jurídica) o 2 (persona natural)")
        @Max(value = 2, message = "El tipo de organización debe ser 1 (persona jurídica) o 2 (persona natural)")
        Integer organizationType,

        @Schema(description = "Responsabilidades fiscales del RUT separadas por ;", example = "O-13;O-47")
        @NotBlank(message = "Las responsabilidades fiscales son obligatorias")
        @Pattern(regexp = "(R-99-PN|O-13|O-15|O-23|O-47)(;(R-99-PN|O-13|O-15|O-23|O-47))*",
                message = "Las responsabilidades fiscales deben ser una o varias de R-99-PN, O-13, O-15, O-23 u O-47, separadas por ;")
        String regimeCode,

        @Schema(description = "Código CIIU de la actividad económica principal (opcional)", example = "4711")
        @Pattern(regexp = "(\\d{4})?", message = "La actividad económica es el código CIIU de 4 dígitos")
        String economicActivity,

        @Schema(description = "Correo de facturación de la empresa", example = "contabilidad@esquina.co")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no es válido")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String email,

        @Schema(description = "Teléfono (opcional)", example = "3001234567")
        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String phone,

        @Schema(description = "Dirección del RUT", example = "Calle 10 # 5-20")
        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
        String address,

        @Schema(description = "Código DANE del municipio (5 dígitos)", example = "05001")
        @NotBlank(message = "El municipio es obligatorio")
        @Pattern(regexp = "\\d{5}", message = "El código DANE del municipio tiene 5 dígitos")
        String cityCode,

        @Schema(description = "Código DANE del departamento (los dos primeros dígitos del municipio)", example = "05")
        @NotBlank(message = "El departamento es obligatorio")
        @Pattern(regexp = "\\d{2}", message = "El código DANE del departamento tiene 2 dígitos")
        String departmentCode
) {

    /** El DV debe ser el de la DIAN para el NIT. Si alguno de los dos tiene mal el formato, solo se reporta eso. */
    @JsonIgnore
    @AssertTrue(message = "El dígito de verificación no corresponde al NIT")
    public boolean isDvOfTheNit() {
        if (nit == null || dv == null || !nit.matches("\\d{3,15}") || !dv.matches("\\d")) {
            return true;
        }
        return DianCheckDigit.matches(nit, dv);
    }

    /** El departamento son los dos primeros dígitos del código DANE del municipio. */
    @JsonIgnore
    @AssertTrue(message = "El departamento no corresponde al municipio (son los dos primeros dígitos de su código DANE)")
    public boolean isDepartmentOfTheCity() {
        if (cityCode == null || departmentCode == null || !cityCode.matches("\\d{5}")
                || !departmentCode.matches("\\d{2}")) {
            return true;
        }
        return cityCode.startsWith(departmentCode);
    }
}
