package com.sciencebot.pos.config.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class PosUserDetails extends org.springframework.security.core.userdetails.User {

    private final Long storeId;

    public PosUserDetails(String username,
                          String password,
                          boolean enabled,
                          Collection<? extends GrantedAuthority> authorities,
                          Long storeId) {
        super(username, password, enabled, true, true, true, authorities);
        this.storeId = storeId;
    }

    public Long getStoreId() {
        return storeId;
    }
}