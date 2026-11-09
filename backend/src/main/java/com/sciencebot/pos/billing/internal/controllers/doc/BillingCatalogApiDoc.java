package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.DivipolaCatalogDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "📄 Facturación Electrónica - Catálogos", description = "Datos de referencia para los formularios fiscales")
public interface BillingCatalogApiDoc {

    @Operation(summary = "Departamentos y municipios (DIVIPOLA del DANE)",
            description = """
                    Los 33 departamentos y 1.122 municipios de Colombia con su código DANE, para elegir el municipio
                    por nombre en los datos fiscales del local y de los clientes. Se carga una vez al arrancar y el
                    navegador puede guardarlo un día (Cache-Control: max-age=86400). Cualquier rol autenticado.
                    """)
    ResponseEntity<DivipolaCatalogDto> getDivipola();
}
