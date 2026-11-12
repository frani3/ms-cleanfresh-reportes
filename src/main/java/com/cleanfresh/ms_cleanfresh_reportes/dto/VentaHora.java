package com.cleanfresh.ms_cleanfresh_reportes.dto;

import java.time.Instant;

/** Pedidos (no cancelados) y monto de una hora; {@code hora} es el inicio de la hora, en UTC. */
public record VentaHora(Instant hora, long pedidos, double monto) {
}
