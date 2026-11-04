package com.sciencebot.pos.users.internal.repositories;

import com.sciencebot.pos.users.internal.entities.UserDeletionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeletionAuditRepository extends JpaRepository<UserDeletionAudit, Long> {
}
