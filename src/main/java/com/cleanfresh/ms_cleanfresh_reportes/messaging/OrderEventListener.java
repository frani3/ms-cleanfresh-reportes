package com.cleanfresh.ms_cleanfresh_reportes.messaging;

import com.cleanfresh.ms_cleanfresh_reportes.service.EventoOrdenService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Entrada de los eventos de Kafka (Spec 044). Solo entrega cada mensaje a {@link EventoOrdenService}, donde está la lógica, y
 * confirma (ACK) cuando termina bien. Si el servicio lanza una excepción no se confirma y entra en juego el manejador de errores
 * ({@code KafkaConsumerConfig}: reintentos y DLT).
 *
 * <p>Pertenece al grupo {@code reportes-kpis}, distinto del de {@code auditoria}: cada grupo recibe todos los eventos. La
 * concurrencia (3 hilos, una por partición) se reparte entre las instancias del grupo.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final EventoOrdenService servicio;

    public OrderEventListener(EventoOrdenService servicio) {
        this.servicio = servicio;
    }

    @KafkaListener(id = "reportes-ordenes", topics = "${app.kafka.topics.orders-events:orders.events}",
            groupId = "${spring.kafka.consumer.group-id}", concurrency = "${spring.kafka.listener.concurrency:3}")
    public void onEvento(ConsumerRecord<String, OrderEventMessage> registro, Acknowledgment confirmacion) {
        OrderEventMessage evento = registro.value();
        EventoOrdenService.Resultado resultado = servicio.procesar(evento);
        confirmacion.acknowledge();
        if (resultado == EventoOrdenService.Resultado.DUPLICADO) {
            log.info("Evento {} ya procesado (partición {}, offset {}): se ignora", evento.eventId(), registro.partition(),
                    registro.offset());
        } else {
            log.info("Evento {} de {} procesado (partición {}, offset {}, traceId {})", evento.type(), evento.correlationId(),
                    registro.partition(), registro.offset(), evento.traceId());
        }
    }
}
