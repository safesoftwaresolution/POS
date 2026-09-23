package com.sciencebot.pos.settings.internal.mappers;

import com.sciencebot.pos.settings.SettingsDto;
import com.sciencebot.pos.settings.internal.entities.Setting;
import org.springframework.stereotype.Component;

@Component
public class SettingsMapper {

    public SettingsDto toDto(Setting setting) {
        if (setting == null) {
            return null;
        }

        return new SettingsDto(
                setting.getBusinessName(),
                setting.getAddress(),
                setting.getPhone(),
                setting.getTaxId(),
                setting.getEmail(),
                setting.getLogoUrl()
        );
    }
}
