package com.sciencebot.pos.businesses;

/** The business of the signed-in owner and the branch their session is working on (from the JWT). */
public record MyBusinessDto(BusinessDto business, Long activeStoreId) {}
