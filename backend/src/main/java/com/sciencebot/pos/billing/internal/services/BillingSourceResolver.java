package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.businesses.BusinessFacade;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * De qué local salen la empresa y la numeración con las que factura un local: el suyo, salvo que pertenezca a un
 * negocio con NIT compartido, en cuyo caso es la sucursal principal. Se consulta al reservar la emisión y el
 * resultado queda fijado en el documento.
 */
@Component
public class BillingSourceResolver {

    /** Diferido: businesses depende de stores y users, y billing ya es consultado por varios módulos. */
    private final ObjectProvider<BusinessFacade> businessFacadeProvider;

    public BillingSourceResolver(ObjectProvider<BusinessFacade> businessFacadeProvider) {
        this.businessFacadeProvider = businessFacadeProvider;
    }

    public Long sourceStoreOf(Long storeId) {
        if (storeId == null) {
            return null;
        }
        BusinessFacade businessFacade = businessFacadeProvider.getIfAvailable();
        return businessFacade == null ? storeId : businessFacade.billingSourceStoreId(storeId);
    }
}
