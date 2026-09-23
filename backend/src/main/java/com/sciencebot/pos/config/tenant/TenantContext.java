package com.sciencebot.pos.config.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> STORE_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setStoreId(Long storeId) {
        STORE_ID.set(storeId);
    }

    public static Long getStoreId() {
        return STORE_ID.get();
    }

    public static void clear() {
        STORE_ID.remove();
    }
}