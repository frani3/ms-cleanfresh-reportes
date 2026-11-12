package com.cleanfresh.ms_cleanfresh_reportes.dto;

/** Un servicio y cuánto se pidió en el rango (pedidos no cancelados). */
public record TopServicio(String servicio, long pedidos, double monto) {
}
