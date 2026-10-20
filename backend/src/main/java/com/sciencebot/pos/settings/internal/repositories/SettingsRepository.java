package com.sciencebot.pos.settings.internal.repositories;

import com.sciencebot.pos.settings.internal.entities.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Setting, Long> {
}
