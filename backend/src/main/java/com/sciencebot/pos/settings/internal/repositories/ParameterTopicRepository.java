package com.sciencebot.pos.settings.internal.repositories;

import com.sciencebot.pos.settings.internal.entities.ParameterTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ParameterTopicRepository extends JpaRepository<ParameterTopic, Long> {

    Optional<ParameterTopic> findByCode(String code);

    boolean existsByCode(String code);

    List<ParameterTopic> findAllByOrderByNameAsc();
}
