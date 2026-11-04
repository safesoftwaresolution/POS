package com.sciencebot.pos.shared.email;

import org.springframework.web.util.HtmlUtils;

/**
 * Plantilla HTML común de los correos de PosBento: cabecera con la marca, tarjeta con el contenido, botón de
 * acción y pie. Está hecha con tablas y estilos en línea (lo único que respetan de forma fiable Gmail, Outlook y
 * los clientes móviles). El logo es un PNG alojado en el frontend (los clientes de correo no muestran SVG); si el
 * cliente bloquea imágenes, se ve el texto alternativo "PosBento" sobre la cabecera oscura.
 *
 * <p>{@code bodyHtml} es HTML de confianza armado por quien llama: debe escapar con {@link #escape} cualquier valor
 * que venga del usuario (nombres, etc.).
 */
public final class EmailTemplate {

    private static final String BRAND = "#FF5520";
    private static final String DARK = "#0E0E12";
    private static final String INK = "#17171B";
    private static final String SOFT = "#5B5B66";
    private static final String MUTED = "#9A9AA5";

    private EmailTemplate() {
    }

    public static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * @param frontendUrl origen público del SPA (APP_FRONTEND_URL): de ahí se descarga {@code /email-logo.png}
     * @param preheader  texto corto que algunos clientes muestran junto al asunto en la bandeja de entrada
     * @param title      título grande de la tarjeta
     * @param bodyHtml   contenido (párrafos {@code <p>}); ya escapado por quien llama
     * @param buttonText texto del botón principal (omitir con null para un correo sin botón)
     * @param buttonUrl  destino del botón; también se muestra como enlace de respaldo
     * @param footerNote aclaración final (por ejemplo, "si no fuiste tú, ignora este mensaje")
     */
    public static String render(String frontendUrl, String preheader, String title, String bodyHtml,
                                String buttonText, String buttonUrl, String footerNote) {
        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light\">")
                .append("<title>").append(escape(title)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:#F4F4F6;\">")
                // Preheader oculto
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">")
                .append(escape(preheader)).append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:#F4F4F6;padding:32px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:560px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;\">")

                // Cabecera con la marca
                .append("<tr><td align=\"center\" style=\"background:").append(DARK)
                .append(";border-radius:20px 20px 0 0;padding:28px 24px;\">")
                .append("<img src=\"").append(escape(stripTrailingSlash(frontendUrl))).append("/email-logo.png\" ")
                .append("alt=\"PosBento\" width=\"176\" height=\"28\" ")
                .append("style=\"display:block;margin:0 auto;border:0;width:176px;height:28px;")
                .append("font-size:24px;font-weight:800;color:#FFFFFF;\">")
                .append("<div style=\"height:3px;width:44px;background:").append(BRAND)
                .append(";border-radius:2px;margin:14px auto 0;\"></div>")
                .append("</td></tr>")

                // Tarjeta de contenido
                .append("<tr><td style=\"background:#FFFFFF;padding:36px 32px 28px;border-radius:0 0 20px 20px;\">")
                .append("<h1 style=\"margin:0 0 18px;font-size:22px;line-height:1.3;color:").append(INK).append(";\">")
                .append(escape(title)).append("</h1>")
                .append("<div style=\"font-size:15px;line-height:1.65;color:").append(SOFT).append(";\">")
                .append(bodyHtml).append("</div>");

        if (buttonText != null && buttonUrl != null) {
            String url = escape(buttonUrl);
            html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:28px 0 8px;\"><tr>")
                    .append("<td align=\"center\" style=\"background:").append(BRAND).append(";border-radius:12px;\">")
                    .append("<a href=\"").append(url).append("\" style=\"display:inline-block;padding:14px 30px;")
                    .append("font-size:15px;font-weight:700;color:#FFFFFF;text-decoration:none;border-radius:12px;\">")
                    .append(escape(buttonText)).append("</a></td></tr></table>")
                    .append("<p style=\"margin:18px 0 0;font-size:12px;line-height:1.5;color:").append(MUTED).append(";\">")
                    .append("Si el botón no funciona, copia y pega este enlace en tu navegador:<br>")
                    .append("<a href=\"").append(url).append("\" style=\"color:").append(BRAND).append(";word-break:break-all;\">")
                    .append(url).append("</a></p>");
        }

        if (footerNote != null && !footerNote.isBlank()) {
            html.append("<hr style=\"border:none;border-top:1px solid #ECECF0;margin:26px 0 16px;\">")
                    .append("<p style=\"margin:0;font-size:12px;line-height:1.5;color:").append(MUTED).append(";\">")
                    .append(escape(footerNote)).append("</p>");
        }

        html.append("</td></tr>")
                // Pie
                .append("<tr><td align=\"center\" style=\"padding:20px 12px 0;font-size:12px;color:").append(MUTED).append(";\">")
                .append("© PosBento · Punto de venta e inventario para tu negocio<br>")
                .append("Este es un mensaje automático, por favor no respondas a este correo.")
                .append("</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }
}
