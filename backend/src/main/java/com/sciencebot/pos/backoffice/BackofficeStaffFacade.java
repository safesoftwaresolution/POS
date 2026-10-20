package com.sciencebot.pos.backoffice;

import com.sciencebot.pos.users.ChangePasswordCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BackofficeStaffFacade {
    Page<BackofficeStaffDto> listStaff(Pageable pageable);
    BackofficeStaffDto getById(Long id);
    BackofficeStaffDto createStaff(CreateBackofficeStaffCommand command);
    BackofficeStaffDto updateStaff(Long id, UpdateBackofficeStaffCommand command);
    void changeStatus(Long id, boolean active);
    void deleteStaff(Long id);
    void changePassword(Long id, ChangePasswordCommand command);
}
