package com.cleanfresh.ms_cleanfresh_reportes.messaging;

/**
 * El evento no cumple el contrato (campos obligatorios ausentes, tipo desconocido, fecha ilegible...). Reintentarlo no lo arregla,
 * así que va directo a su DLT (ver {@code KafkaConsumerConfig}).
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
