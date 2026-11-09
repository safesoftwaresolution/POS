package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.services.CreditNoteService;
import com.sciencebot.pos.billing.internal.services.DebitNoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BillingNoteRetryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CreditNoteService credits;
    @MockitoBean DebitNoteService debits;

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanRecoverBothNoteTypes() throws Exception {
        mvc.perform(post("/api/v1/billing/credit-notes/12/retry")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/billing/debit-notes/13/retry")).andExpect(status().isOk());
        verify(credits).retry(12L);
        verify(debits).retry(13L);
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void sellerCannotRecoverEitherNoteType() throws Exception {
        mvc.perform(post("/api/v1/billing/credit-notes/12/retry")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/billing/debit-notes/13/retry")).andExpect(status().isForbidden());
        verifyNoInteractions(credits, debits);
    }
}
