package com.sciencebot.pos.config.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.lang.reflect.Method;

/**
 * Habilita metodos @Async (usado hoy solo por las notificaciones de stock bajo del modulo
 * inventory: el envio de correo no debe bloquear ni poder tumbar el registro de una venta o
 * compra). Cualquier excepcion no capturada dentro de un metodo @Async void se loguea aqui en
 * vez de perderse silenciosamente o propagarse al hilo que la disparo.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.warn("Fallo no capturado en tarea asincrona {}: {}", method.getName(), ex.getMessage(), ex);
            }
        };
    }
}
