package com.cleanfresh.ms_cleanfresh_reportes.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Indicadores del rango pedido. {@code ventas} suma el total de los pedidos no cancelados creados en el rango;
 * {@code estadosActivos} cuenta las órdenes que hoy siguen en curso (sin importar cuándo se crearon).
 */
public record Kpis(String rango, Instant desde, Instant hasta, long pedidos, long pedidosNoCancelados, double ventas,
                   double ticketPromedio, long entregados, long cancelados, LeadTime leadTime,
                   Map<String, Long> estadosActivos) {
}
