package com.sciencebot.pos.shared;

import com.sciencebot.pos.shared.email.EmailTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateTest {

    @Test
    void render_IncludesBrandTitleBodyButtonAndFallbackLink() {
        String html = EmailTemplate.render("https://app.posbento.com/", "Resumen", "Titulo", "<p>Cuerpo</p>",
                "Entrar", "https://posbento.com/login", "Nota final");

        assertTrue(html.contains("src=\"https://app.posbento.com/email-logo.png\""));
        assertTrue(html.contains("PosBento"));
        assertFalse(html.contains("ProPOS"));
        assertTrue(html.contains("Titulo"));
        assertTrue(html.contains("<p>Cuerpo</p>"));
        assertTrue(html.contains("href=\"https://posbento.com/login\""));
        assertTrue(html.contains("Entrar"));
        assertTrue(html.contains("Nota final"));
        assertTrue(html.contains("Resumen"));
    }

    @Test
    void render_WithoutButton_OmitsTheActionBlock() {
        String html = EmailTemplate.render("https://app.posbento.com", "r", "t", "<p>x</p>", null, null, null);

        assertFalse(html.contains("<a href"));
    }

    @Test
    void render_EscapesTitleButtonTextAndUrl() {
        String html = EmailTemplate.render("https://app.posbento.com", "r", "<script>t</script>", "<p>x</p>", "<b>", "https://x/?a=1&b=\"2\"", null);

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&amp;b=&quot;2&quot;"));
    }

    @Test
    void escape_KeepsAccentedTextReadableAsHtmlEntities() {
        assertEquals("Tienda Mar&iacute;a", EmailTemplate.escape("Tienda María"));
    }

    @Test
    void escape_HandlesNullAndMarkup() {
        assertEquals("", EmailTemplate.escape(null));
        assertEquals("&lt;i&gt;Tienda &amp; Co&lt;/i&gt;", EmailTemplate.escape("<i>Tienda & Co</i>"));
    }
}
