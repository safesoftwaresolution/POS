package com.sciencebot.pos.voicesales.internal.controllers;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class VoiceUploadConfiguration {
    @Bean
    FilterRegistrationBean<Filter> voiceMultipartGuard() {
        var registration = new FilterRegistrationBean<Filter>(new VoiceMultipartGuard());
        registration.addUrlPatterns("/api/v1/voice-sales/preview");
        return registration;
    }
}
