package com.sciencebot.pos.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una ruta inexistente llega al handler como NoResourceFoundException; antes el catch-all
 * de GlobalExceptionHandler la devolvia como 500 ("No static resource ...") en vez de 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotFoundRouteHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootPath_IsNotFoundNotInternalError() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No existe el recurso GET /"));
    }

    @Test
    void unknownPath_IsNotFound() throws Exception {
        mockMvc.perform(get("/no-existe/ruta"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe el recurso GET /no-existe/ruta"));
    }
}
