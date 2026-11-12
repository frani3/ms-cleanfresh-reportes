package com.cleanfresh.ms_cleanfresh_reportes;

import com.cleanfresh.ms_cleanfresh_reportes.repository.EventoProcesadoRepository;
import com.cleanfresh.ms_cleanfresh_reportes.repository.OrdenResumenRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec 044, con un Kafka embebido de verdad: el consumidor procesa los eventos buenos, los repetidos no se duplican y los malos
 * (JSON roto o contrato incumplido) no detienen nada: van a {@code orders.events.reportes.DLT} con el mensaje original y el error.
 */
@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.espera-inicial-ms=50"
})
@EmbeddedKafka(partitions = 3, topics = {"orders.events", "orders.events.reportes.DLT"})
class ReportesKafkaTests {

    @Autowired
    EmbeddedKafkaBroker broker;
    @Autowired
    OrdenResumenRepository ordenes;
    @Autowired
    EventoProcesadoRepository procesados;

    private static String json(String tipo, String eventId, String estado, String timestamp) {
        return """
                {"type":"%s","eventId":"%s","timestamp":"%s","traceId":"t-1","correlationId":"ORD-0100",
                 "payload":{"numeroOrden":"ORD-0100","cliente":"cliente-uuid","clienteNombre":"cliente@cleanfresh.com",
                 "servicio":"Planchado","sucursal":"Providencia","total":9500.0,"estado":"%s","fecha":"2026-10-15",
                 "fechaHora":"2026-10-15T14:00:00Z","actor":"op-1"}}
                """.formatted(tipo, eventId, timestamp, estado);
    }

    private KafkaTemplate<String, String> productor() {
        Map<String, Object> config = KafkaTestUtils.producerProps(broker);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new StringSerializer()));
    }

    @Test
    void losEventosBuenosSeProcesanLosRepetidosNoSeDuplicanYLosMalosVanAlDlt() throws Exception {
        KafkaTemplate<String, String> kafka = productor();
        String creado = json("OrderCreated", "ev-1", "CREADO", "2026-10-15T14:00:01Z");

        kafka.send(new ProducerRecord<>("orders.events", "ORD-0100", creado)).get(10, TimeUnit.SECONDS);
        kafka.send(new ProducerRecord<>("orders.events", "ORD-0100", "esto no es json")).get(10, TimeUnit.SECONDS);
        kafka.send(new ProducerRecord<>("orders.events", "ORD-0100", json("OrderInventado", "ev-x", "CREADO", "2026-10-15T14:00:02Z")))
                .get(10, TimeUnit.SECONDS);
        kafka.send(new ProducerRecord<>("orders.events", "ORD-0100", json("OrderAccepted", "ev-2", "ACEPTADO", "2026-10-15T14:05:00Z")))
                .get(10, TimeUnit.SECONDS);
        kafka.send(new ProducerRecord<>("orders.events", "ORD-0100", creado)).get(10, TimeUnit.SECONDS);   // repetido

        // los dos eventos buenos se procesaron aunque haya mensajes malos en medio
        long limite = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < limite && !"ACEPTADO".equals(ordenes.findById("ORD-0100").map(o -> o.getEstado()).orElse(""))) {
            Thread.sleep(200);
        }
        assertEquals("ACEPTADO", ordenes.findById("ORD-0100").orElseThrow().getEstado());
        assertEquals(2, procesados.count(), "el repetido no se cuenta dos veces y los malos no se guardan");

        // los dos malos están en el DLT, con el mensaje original y el motivo
        List<ConsumerRecord<String, String>> enDlt = leerDlt(2);
        assertEquals(2, enDlt.size());
        ConsumerRecord<String, String> roto = enDlt.stream().filter(r -> "esto no es json".equals(r.value())).findFirst().orElseThrow();
        assertEquals("orders.events", cabecera(roto, "kafka_dlt-original-topic"));
        assertNotNull(roto.headers().lastHeader("kafka_dlt-exception-fqcn"));
        assertNotNull(roto.headers().lastHeader("kafka_dlt-exception-stacktrace"));
        assertNotNull(roto.headers().lastHeader("kafka_dlt-original-offset"));
        assertNotNull(roto.headers().lastHeader("kafka_dlt-original-partition"));
        ConsumerRecord<String, String> invalido = enDlt.stream().filter(r -> r.value() != null && r.value().contains("OrderInventado"))
                .findFirst().orElseThrow();
        assertTrue(cabecera(invalido, "kafka_dlt-exception-message").contains("tipo de evento desconocido"));
    }

    private List<ConsumerRecord<String, String>> leerDlt(int esperados) {
        Map<String, Object> config = KafkaTestUtils.consumerProps("lector-dlt", "true", broker);
        config.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumidor = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer())
                .createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumidor, "orders.events.reportes.DLT");
            List<ConsumerRecord<String, String>> todos = new ArrayList<>();
            long limite = System.currentTimeMillis() + 30_000;
            while (todos.size() < esperados && System.currentTimeMillis() < limite) {
                ConsumerRecords<String, String> lote = consumidor.poll(Duration.ofMillis(500));
                lote.forEach(todos::add);
            }
            return todos;
        }
    }

    private static String cabecera(ConsumerRecord<String, String> registro, String nombre) {
        Header h = registro.headers().lastHeader(nombre);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
