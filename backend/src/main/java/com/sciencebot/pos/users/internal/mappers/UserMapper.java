package com.sciencebot.pos.users.internal.mappers;

import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.internal.entities.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toDto(User user) {
        if (user == null) {
            return null;
        }

        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getStoreId(),
                user.isEmailVerified()
        );
    }
}