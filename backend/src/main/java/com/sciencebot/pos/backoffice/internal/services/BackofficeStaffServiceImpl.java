package com.sciencebot.pos.backoffice.internal.services;

import com.sciencebot.pos.auth.AuthFacade;
import com.sciencebot.pos.backoffice.*;
import com.sciencebot.pos.users.ChangePasswordCommand;
import com.sciencebot.pos.users.CreateUserCommand;
import com.sciencebot.pos.users.UpdateUserCommand;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BackofficeStaffServiceImpl implements BackofficeStaffFacade {

    private static final String STAFF_ROLE = "SUPER_ADMIN";

    private final UserFacade userFacade;
    private final AuthFacade authFacade;

    public BackofficeStaffServiceImpl(UserFacade userFacade, AuthFacade authFacade) {
        this.userFacade = userFacade;
        this.authFacade = authFacade;
    }

    @Override
    public Page<BackofficeStaffDto> listStaff(Pageable pageable) {
        return userFacade.listUsersByRole(STAFF_ROLE, pageable).map(this::toStaffDto);
    }

    @Override
    public BackofficeStaffDto getById(Long id) {
        return toStaffDto(requireStaff(id));
    }

    @Override
    @Transactional
    public BackofficeStaffDto createStaff(CreateBackofficeStaffCommand command) {
        // Se crea como invitacion: no puede entrar hasta aceptar el enlace del correo. Si el correo no sale, se
        // revierte la creacion (el error llega a quien invita) en vez de dejar una cuenta que nadie puede activar.
        UserDto created = userFacade.createUnverifiedUser(new CreateUserCommand(
                command.fullName(), command.username(), command.email(), command.password(), STAFF_ROLE, null
        ));
        authFacade.resendVerificationToUser(created.id());
        return toStaffDto(created);
    }

    @Override
    @Transactional
    public BackofficeStaffDto updateStaff(Long id, UpdateBackofficeStaffCommand command) {
        requireStaff(id);
        UserDto updated = userFacade.updateUser(id, new UpdateUserCommand(command.fullName(), command.email(), STAFF_ROLE));
        return toStaffDto(updated);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, boolean active) {
        requireStaff(id);
        userFacade.changeStatus(id, active);
    }

    @Override
    @Transactional
    public void deleteStaff(Long id) {
        requireStaff(id);
        userFacade.deleteUser(id);
    }

    @Override
    @Transactional
    public void changePassword(Long id, ChangePasswordCommand command) {
        requireStaff(id);
        userFacade.changePassword(id, command);
    }

    @Override
    public void resendInvitation(Long id) {
        requireStaff(id);
        authFacade.resendVerificationToUser(id);
    }

    private UserDto requireStaff(Long id) {
        UserDto user = userFacade.getById(id);
        if (!STAFF_ROLE.equals(user.role())) {
            throw new EntityNotFoundException("Operador de backoffice no encontrado con ID: " + id);
        }
        return user;
    }

    private BackofficeStaffDto toStaffDto(UserDto user) {
        return new BackofficeStaffDto(user.id(), user.fullName(), user.username(), user.email(), user.active(), user.emailVerified());
    }
}
