package com.cleanfresh.ms_cleanfresh_reportes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Kafka de este servicio (Spec 044). {@code enabled} enciende el consumidor (por defecto apagado: en local y en las pruebas no hace
 * falta un clúster). La conexión y el consumidor (brokers, grupo, deserializers, commit manual...) están en
 * {@code spring.kafka.*} de application.yaml; acá van los nombres de los tópicos y la política de errores.
 *
 * @param topics            tópicos que consume
 * @param consumidorDlt     nombre de este consumidor dentro del DLT: los mensajes fallidos de {@code orders.events} van a
 *                          {@code orders.events.<consumidorDlt>.DLT}
 * @param reintentos        cuántas veces más se reintenta un mensaje que falla por un error pasajero (la base caída un momento...)
 * @param esperaInicialMs   espera antes del primer reintento; se duplica en cada uno
 * @param esperaMaximaMs    tope de la espera entre reintentos
 */
@ConfigurationProperties(prefix = "app.kafka")
public record AppKafkaProperties(boolean enabled, Topics topics, String consumidorDlt, int reintentos, long esperaInicialMs,
                                 long esperaMaximaMs) {

    public AppKafkaProperties {
        topics = topics == null ? new Topics(null) : topics;
        consumidorDlt = consumidorDlt == null || consumidorDlt.isBlank() ? "reportes" : consumidorDlt;
        reintentos = reintentos <= 0 ? 3 : reintentos;
        esperaInicialMs = esperaInicialMs <= 0 ? 500 : esperaInicialMs;
        esperaMaximaMs = esperaMaximaMs <= 0 ? 5000 : esperaMaximaMs;
    }

    public record Topics(String ordersEvents) {

        public Topics {
            ordersEvents = ordersEvents == null || ordersEvents.isBlank() ? "orders.events" : ordersEvents;
        }

        public List<String> todos() {
            return List.of(ordersEvents);
        }
    }

    /** Nombre del Dead Letter Topic de un tópico para este consumidor. */
    public String dlt(String topico) {
        return topico + "." + consumidorDlt + ".DLT";
    }
}
