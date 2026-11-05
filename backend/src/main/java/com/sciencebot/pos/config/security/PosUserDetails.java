package com.sciencebot.pos.config.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class PosUserDetails extends org.springframework.security.core.userdetails.User {

    private final Long storeId;
    /** Estado del local del usuario (ACTIVE, PENDING_VERIFICATION...) al cargarlo; null si no tiene local. */
    private final String storeStatus;

    public PosUserDetails(String username,
                          String password,
                          boolean enabled,
                          Collection<? extends GrantedAuthority> authorities,
                          Long storeId) {
        this(username, password, enabled, authorities, storeId, null);
    }

    public PosUserDetails(String username,
                          String password,
                          boolean enabled,
                          Collection<? extends GrantedAuthority> authorities,
                          Long storeId,
                          String storeStatus) {
        super(username, password, enabled, true, true, true, authorities);
        this.storeId = storeId;
        this.storeStatus = storeStatus;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getStoreStatus() {
        return storeStatus;
    }
}
