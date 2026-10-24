package com.sciencebot.pos.support;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Se acepta sin autenticar, asi que se acotan los campos de texto libre. */
public record CreateTicketCommand(
    @NotBlank @Size(max = 30) String type,
    @Size(max = 20) String priority,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 4000) String description,
    @Size(max = 150) String contactName,
    @Email @Size(max = 150) String contactEmail,
    @Size(max = 30) String contactPhone,
    Long storeId,
    @Size(max = 2000) String systemInfo
) {}
