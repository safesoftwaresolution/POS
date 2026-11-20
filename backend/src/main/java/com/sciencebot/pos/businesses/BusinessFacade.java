package com.sciencebot.pos.businesses;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BusinessFacade {
    /** Creates a business around an existing store and its ADMINISTRATOR (SUPER_ADMIN; audited). */
    BusinessDto create(CreateBusinessCommand command);

    BusinessDto getById(Long id);

    Page<BusinessDto> search(String q, Pageable pageable);

    /** Changes the name and/or the branch limit; the limit cannot go below the current number of branches. */
    BusinessDto update(Long id, UpdateBusinessCommand command);

    /** Adds an existing store as a branch (SUPER_ADMIN; audited). Respects the branch limit. */
    BusinessDto addBranch(Long businessId, Long storeId);

    /** Business owned by the user (the "parent admin"), or empty when the user owns none. */
    java.util.Optional<BusinessDto> findByOwnerUsername(String username);

    /**
     * Status of the branch when the user owns a business that includes it, or empty when they do not
     * (the store is standalone, belongs to another business, or the user is not the owner). This is the
     * only check that lets a session act on a store other than the user's own.
     */
    java.util.Optional<String> accessibleBranchStatus(String username, Long storeId);

    /**
     * Creates a new branch for the owner's business (pending review by the SUPER_ADMIN like any store). It
     * respects the branch limit, inherits the main store's category when none is given, and in
     * SHARED_TAX_ID mode takes the tax id from the main store.
     */
    BranchDto createBranch(String ownerUsername, com.sciencebot.pos.stores.CreateStoreCommand command);

    /** Dissolves the business: every store becomes standalone again and the business is deleted (audited). */
    void dissolve(Long id);

    /** Name of the business the user owns, if any. Used to protect the owner from being deleted. */
    java.util.Optional<String> businessNameOwnedBy(Long userId);

    /**
     * Store whose Alegra company and numbering ranges the given store invoices with: the main store of its business
     * when the business uses {@code SHARED_TAX_ID}, otherwise the store itself.
     */
    Long billingSourceStoreId(Long storeId);

    /**
     * Changes how the branches invoice (SUPER_ADMIN; audited). Refused once any store of the business has
     * invoices validated by the DIAN, because those documents are bound to the company they were issued with.
     */
    BusinessDto changeBillingMode(Long id, BillingMode billingMode);

    /** Removes a branch from the business without deleting the store; the main store cannot be removed. */
    BusinessDto removeBranch(Long businessId, Long storeId);
}
