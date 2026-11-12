package com.cleanfresh.ms_cleanfresh_reportes.service;

import java.time.Duration;
import java.util.Locale;

/** Ventana de tiempo de un reporte: la última hora, las últimas 24 horas, los últimos 7 días o los últimos 30 días. */
public enum Rango {

    LAST1H("last1h", Duration.ofHours(1)),
    LAST24H("last24h", Duration.ofHours(24)),
    LAST7D("last7d", Duration.ofDays(7)),
    LAST30D("last30d", Duration.ofDays(30));

    private final String clave;
    private final Duration duracion;

    Rango(String clave, Duration duracion) {
        this.clave = clave;
        this.duracion = duracion;
    }

    public String clave() {
        return clave;
    }

    public Duration duracion() {
        return duracion;
    }

    /** Convierte {@code last24h} y similares; sin valor usa {@code porDefecto}; otro texto lanza IllegalArgumentException. */
    public static Rango de(String texto, Rango porDefecto) {
        if (texto == null || texto.isBlank()) {
            return porDefecto;
        }
        String buscado = texto.trim().toLowerCase(Locale.ROOT);
        for (Rango r : values()) {
            if (r.clave.equals(buscado)) {
                return r;
            }
        }
        throw new IllegalArgumentException("range inválido: " + texto + " (use last1h, last24h, last7d o last30d)");
    }
}
