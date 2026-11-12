package com.cleanfresh.ms_cleanfresh_reportes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Evento de Kafka ya procesado. Su {@code eventId} es la clave de idempotencia: Kafka entrega "al menos una vez", así que un evento
 * puede llegar repetido (un reintento, un rebalanceo) y no debe sumarse dos veces.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "numero_orden", nullable = false, length = 40)
    private String numeroOrden;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
    }

    public EventoProcesado(String eventId, String tipo, String numeroOrden, Instant procesadoEn) {
        this.eventId = eventId;
        this.tipo = tipo;
        this.numeroOrden = numeroOrden;
        this.procesadoEn = procesadoEn;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }
}
