package com.sciencebot.pos.voicesales.internal.controllers;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;

/**
 * Rejects multipart before DispatcherServlet can spool the request into temporary files. DispatcherServlet
 * resolves multipart before it looks up a handler and for any method, so the guard does not filter by method.
 */
final class VoiceMultipartGuard implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String type = request.getContentType();
        if (type != null && type.stripLeading().toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            var reply = (HttpServletResponse) response;
            reply.setStatus(415);
            reply.setHeader("Cache-Control", "no-store");
            reply.setContentType("application/json;charset=UTF-8");
            reply.getWriter().write("{\"message\":\"Envía el audio como cuerpo binario, no multipart.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
