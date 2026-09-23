package com.sciencebot.pos.billing.internal.adapters.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FactusTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("refresh_token") String refreshToken) {

}
