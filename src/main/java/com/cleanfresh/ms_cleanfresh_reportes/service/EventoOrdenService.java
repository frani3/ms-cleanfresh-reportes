package com.cleanfresh.ms_cleanfresh_reportes.service;

import com.cleanfresh.ms_cleanfresh_reportes.entity.EventoProcesado;
import com.cleanfresh.ms_cleanfresh_reportes.entity.OrdenResumen;
import com.cleanfresh.ms_cleanfresh_reportes.messaging.EventoInvalidoException;
import com.cleanfresh.ms_cleanfresh_reportes.messaging.OrderEventMessage;
import com.cleanfresh.ms_cleanfresh_reportes.repository.EventoProcesadoRepository;
import com.cleanfresh.ms_cleanfresh_reportes.repository.OrdenResumenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * La lógica de negocio de los eventos de órdenes (Spec 044), sin nada de Kafka: valida el evento, descarta los repetidos y deja al
 * día el resumen de la orden con el que se calculan los reportes. El listener solo le entrega los mensajes y confirma.
 */
@Service
public class EventoOrdenService {

    public enum Resultado { PROCESADO, DUPLICADO }

    static final Set<String> TIPOS = Set.of("OrderCreated", "OrderAccepted", "OrderPreparing", "OrderDispatched",
            "OrderDelivered", "OrderCancelled");

    private final OrdenResumenRepository ordenes;
    private final EventoProcesadoRepository procesados;
    private final Clock reloj;

    public EventoOrdenService(OrdenResumenRepository ordenes, EventoProcesadoRepository procesados, Clock reloj) {
        this.ordenes = ordenes;
        this.procesados = procesados;
        this.reloj = reloj;
    }

    /**
     * Procesa un evento en una sola transacción (el resumen y la marca de "ya procesado" se guardan juntos). Un evento con el mismo
     * {@code eventId} ya visto no cambia nada. Un evento viejo no pisa a uno más nuevo de la misma orden. Lanza
     * {@link EventoInvalidoException} si el evento no cumple el contrato.
     */
    @Transactional
    public Resultado procesar(OrderEventMessage evento) {
        Instant ocurrido = validar(evento);
        if (procesados.existsById(evento.eventId())) {
            return Resultado.DUPLICADO;
        }
        OrderEventMessage.Payload p = evento.payload();
        OrdenResumen orden = ordenes.findById(p.numeroOrden()).orElseGet(() -> new OrdenResumen(p.numeroOrden()));

        // Los eventos de una orden llegan en orden (misma clave, misma partición); esto protege además de una relectura desde el inicio.
        boolean esMasNuevo = orden.getActualizadaEn() == null || !ocurrido.isBefore(orden.getActualizadaEn());
        if (esMasNuevo) {
            orden.setEstado(p.estado());
            orden.setSucursal(p.sucursal());
            orden.setServicio(p.servicio());
            orden.setTotal(p.total());
            orden.setActualizadaEn(ocurrido);
        }
        if (orden.getCreadaEn() == null) {
            orden.setCreadaEn(instanteDeCreacion(evento, ocurrido));
        }
        if ("OrderDelivered".equals(evento.type())) {
            orden.setEntregadaEn(ocurrido);
        }
        ordenes.save(orden);
        procesados.save(new EventoProcesado(evento.eventId(), evento.type(), p.numeroOrden(), reloj.instant()));
        return Resultado.PROCESADO;
    }

    /** Cuándo se creó la orden: el {@code fechaHora} del evento; si no viene (órdenes antiguas), el momento del primer evento visto. */
    private static Instant instanteDeCreacion(OrderEventMessage evento, Instant ocurrido) {
        String fechaHora = evento.payload().fechaHora();
        if (fechaHora != null && !fechaHora.isBlank()) {
            try {
                return Instant.parse(fechaHora);
            } catch (DateTimeParseException e) {
                throw new EventoInvalidoException("fechaHora ilegible: " + fechaHora);
            }
        }
        return ocurrido;
    }

    /** Comprueba el contrato del evento y devuelve cuándo ocurrió. */
    private static Instant validar(OrderEventMessage e) {
        if (e == null || e.payload() == null) {
            throw new EventoInvalidoException("evento vacío o sin payload");
        }
        if (vacio(e.eventId())) {
            throw new EventoInvalidoException("falta eventId");
        }
        if (e.type() == null || !TIPOS.contains(e.type())) {
            throw new EventoInvalidoException("tipo de evento desconocido: " + e.type());
        }
        OrderEventMessage.Payload p = e.payload();
        if (vacio(p.numeroOrden())) {
            throw new EventoInvalidoException("falta numeroOrden");
        }
        if (vacio(p.estado())) {
            throw new EventoInvalidoException("falta estado");
        }
        if (p.total() == null || p.total() < 0 || p.total().isNaN()) {
            throw new EventoInvalidoException("total inválido: " + p.total());
        }
        try {
            return Instant.parse(e.timestamp());
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new EventoInvalidoException("timestamp ilegible: " + e.timestamp());
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
