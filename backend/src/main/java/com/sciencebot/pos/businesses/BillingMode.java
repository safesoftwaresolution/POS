package com.sciencebot.pos.businesses;

/** How the branches of a business invoice: each with its own tax id (company) or all with the main branch's. */
public enum BillingMode {
    SEPARATE_TAX_ID,
    SHARED_TAX_ID
}
