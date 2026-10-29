package com.devarena.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    /** Injected wherever time matters so tests can control it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
