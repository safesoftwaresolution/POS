package com.sciencebot.pos.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("POS System API")
                        .version("1.0.0")
                        .description("""
                                ## Sistema de Punto de Venta (POS)
                                
                                API REST completa para la gestión de un sistema de punto de venta con facturación electrónica a través de **Factus**.
                                
                                ### Autenticación
                                Esta API usa **JWT Bearer Token**. Para obtener un token:
                                1. Llama a `POST /api/v1/auth/login` con `username` y `password`.
                                2. Copia el valor `token` de la respuesta.
                                3. Haz clic en el botón **Authorize 🔒** e ingresa: `Bearer <tu_token>`.
                                
                                ### Roles disponibles
                                | Rol | Descripción |
                                |-----|-------------|
                                | `ADMINISTRATOR` | Acceso completo a todos los módulos |
                                | `SUPERVISOR` | Gestión de productos, inventario, compras, ventas |
                                | `SELLER` | Registro de ventas, consulta de productos y clientes |
                                
                                ### Paginación
                                Los endpoints de listado soportan los parámetros:
                                - `page` (default: `0`) — número de página
                                - `size` (default: `20`) — elementos por página
                                
                                ### Formato de fechas
                                Usar formato ISO 8601: `2025-01-15T08:00:00`
                                """)
                        .contact(new Contact()
                                .name("POS Team")
                                .email("soporte@example.com")
                                .url("https://example.com"))
                        .license(new License()
                                .name("Propietario")
                                .url("https://example.com/licencia")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Servidor local de desarrollo"),
                        new Server()
                                .url("https://api.example.com")
                                .description("Servidor de producción")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Ingresa tu JWT Token. Ejemplo: `Bearer eyJhbGciOiJIUzI1NiJ9...`")))
                .tags(List.of(
                        new Tag().name("🔐 Autenticación")
                                .description("Login y gestión de sesión JWT"),
                        new Tag().name("👤 Usuarios")
                                .description("Gestión de usuarios del sistema (solo ADMINISTRATOR)"),
                        new Tag().name("🏷️ Categorías")
                                .description("Gestión de categorías de productos"),
                        new Tag().name("📦 Productos")
                                .description("Catálogo de productos, precios y disponibilidad"),
                        new Tag().name("🏭 Proveedores")
                                .description("Gestión de proveedores de la empresa"),
                        new Tag().name("🧾 Compras")
                                .description("Registro y consulta de órdenes de compra"),
                        new Tag().name("📊 Inventario")
                                .description("Movimientos de inventario: entradas, salidas y ajustes"),
                        new Tag().name("👥 Clientes")
                                .description("Gestión del directorio de clientes"),
                        new Tag().name("💰 Ventas")
                                .description("Registro y consulta de transacciones de venta"),
                        new Tag().name("📄 Facturación Electrónica")
                                .description("Integración con Factus para facturación electrónica DIAN"),
                        new Tag().name("📈 Reportes")
                                .description("Reportes de ventas, stock, rentabilidad y compras (solo ADMINISTRATOR)"),
                        new Tag().name("⚙️ Configuración")
                                .description("Parámetros globales del negocio")));
    }

    @Bean
    public org.springdoc.core.customizers.OperationCustomizer securityResponsesCustomizer() {
        return (operation, handlerMethod) -> {
            boolean hasSecurity = handlerMethod.hasMethodAnnotation(org.springframework.security.access.prepost.PreAuthorize.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(org.springframework.security.access.prepost.PreAuthorize.class);
            if (hasSecurity && operation.getResponses() != null) {
                if (!operation.getResponses().containsKey("401")) {
                    operation.getResponses().addApiResponse("401",
                            new io.swagger.v3.oas.models.responses.ApiResponse().description("No autenticado (Token JWT ausente, inválido o expirado)"));
                }
                if (!operation.getResponses().containsKey("403")) {
                    operation.getResponses().addApiResponse("403",
                            new io.swagger.v3.oas.models.responses.ApiResponse().description("Acceso denegado (Rol o permisos insuficientes)"));
                }
            }
            return operation;
        };
    }
}
