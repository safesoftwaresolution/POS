package com.sciencebot.pos.settings;

import com.sciencebot.pos.settings.internal.controllers.CompanyLogoController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyLogoControllerTest {

    @Mock
    private CompanyLogoService companyLogoService;

    @InjectMocks
    private CompanyLogoController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void updateLogo_withFileParam_succeeds() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});
        CompanyLogoResponseDto expectedDto = new CompanyLogoResponseDto(
                "https://pub.r2.dev/logos/logo.png",
                "logo.png",
                250,
                250,
                3L,
                true,
                "OK"
        );

        when(companyLogoService.updateLogo(file)).thenReturn(expectedDto);

        ResponseEntity<CompanyLogoResponseDto> response = controller.updateLogo(file, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedDto, response.getBody());
        verify(companyLogoService, times(1)).updateLogo(file);
    }

    @Test
    void updateLogo_withImageParam_succeeds() {
        MockMultipartFile image = new MockMultipartFile("image", "logo.jpg", "image/jpeg", new byte[]{1, 2, 3});
        CompanyLogoResponseDto expectedDto = new CompanyLogoResponseDto(
                "https://pub.r2.dev/logos/logo.jpg",
                "logo.jpg",
                300,
                300,
                3L,
                true,
                "OK"
        );

        when(companyLogoService.updateLogo(image)).thenReturn(expectedDto);

        ResponseEntity<CompanyLogoResponseDto> response = controller.updateLogo(null, image);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedDto, response.getBody());
        verify(companyLogoService, times(1)).updateLogo(image);
    }

    @Test
    void updateLogo_withoutAnyFile_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> controller.updateLogo(null, null)
        );

        assertTrue(ex.getMessage().contains("Debe adjuntar un archivo"));
        verifyNoInteractions(companyLogoService);
    }
}
