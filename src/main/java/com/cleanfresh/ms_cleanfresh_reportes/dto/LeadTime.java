package com.cleanfresh.ms_cleanfresh_reportes.dto;

/** Cuánto tardan las órdenes entre que se crean y se entregan, en minutos. Sin entregas en el rango, los valores son null y {@code muestras} 0. */
public record LeadTime(Double promedioMin, Double minimoMin, Double maximoMin, int muestras) {
}
