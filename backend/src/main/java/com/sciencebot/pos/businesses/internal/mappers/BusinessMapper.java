package com.sciencebot.pos.businesses.internal.mappers;

import com.sciencebot.pos.businesses.BranchDto;
import com.sciencebot.pos.businesses.BusinessDto;
import com.sciencebot.pos.businesses.internal.entities.BusinessEntity;
import com.sciencebot.pos.stores.StoreDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessMapper {

    public BusinessDto toDto(BusinessEntity business, List<StoreDto> stores) {
        List<BranchDto> branches = stores.stream()
                .map(s -> new BranchDto(s.id(), s.name(), s.status(), s.id().equals(business.getMainStoreId())))
                .toList();
        return new BusinessDto(
                business.getId(),
                business.getName(),
                business.getOwnerUserId(),
                business.getMainStoreId(),
                business.getBillingMode(),
                business.getCatalogMode(),
                business.getMaxBranches(),
                branches,
                business.getCreatedAt());
    }
}
