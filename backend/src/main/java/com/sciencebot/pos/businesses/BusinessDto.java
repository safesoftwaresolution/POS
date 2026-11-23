package com.sciencebot.pos.businesses;

import java.time.LocalDateTime;
import java.util.List;

public record BusinessDto(
        Long id,
        String name,
        Long ownerUserId,
        Long mainStoreId,
        BillingMode billingMode,
        CatalogMode catalogMode,
        /** How many branches the business may have besides its main store. */
        int maxBranches,
        List<BranchDto> branches,
        LocalDateTime createdAt
) {}
