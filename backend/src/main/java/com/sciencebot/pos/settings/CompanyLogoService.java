package com.sciencebot.pos.settings;

import org.springframework.web.multipart.MultipartFile;

/**
 * Servicio público para la validación, actualización y sincronización del logo de la empresa.
 */
public interface CompanyLogoService {

    /**
     * Valida la imagen (formato PNG/JPG/JPEG, peso menor a 200 KB, dimensiones <= 300x300 px),
     * actualiza la propiedad logoUrl de la entidad Setting en la base de datos local y
     * sincroniza el logo con la API de Factus (POST /v2/companies/logo).
     *
     * @param file Archivo multipart con la imagen del logo.
     * @return DTO con la información del logo actualizado y estado de sincronización.
     */
    CompanyLogoResponseDto updateLogo(MultipartFile file);
}
