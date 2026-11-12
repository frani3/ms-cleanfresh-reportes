package com.cleanfresh.ms_cleanfresh_reportes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** El reloj del servicio, inyectable para que las pruebas fijen "ahora". */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
