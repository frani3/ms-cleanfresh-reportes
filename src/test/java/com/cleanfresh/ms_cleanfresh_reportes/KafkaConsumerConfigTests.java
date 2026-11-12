package com.cleanfresh.ms_cleanfresh_reportes;

import com.cleanfresh.ms_cleanfresh_reportes.config.AppKafkaProperties;
import com.cleanfresh.ms_cleanfresh_reportes.config.KafkaConsumerConfig;
import com.cleanfresh.ms_cleanfresh_reportes.messaging.EventoInvalidoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Spec 044: qué se reintenta y qué va directo al DLT, y cómo se llama el DLT de este consumidor. */
class KafkaConsumerConfigTests {

    private final AppKafkaProperties props = new AppKafkaProperties(true, null, null, 0, 0, 0);

    @Test
    void losValoresPorDefectoSonLosDeLaSpec() {
        assertEquals("orders.events", props.topics().ordersEvents());
        assertEquals("reportes", props.consumidorDlt());
        assertEquals(3, props.reintentos());
        assertEquals(500, props.esperaInicialMs());
        assertEquals(5000, props.esperaMaximaMs());
    }

    @Test
    void elDltEsTopicoPuntoConsumidorPuntoDLT() {
        assertEquals("orders.events.reportes.DLT", props.dlt("orders.events"));
    }

    @Test
    void unEventoInvalidoOUnJsonRotoNoSeReintentan() {
        CommonErrorHandler manejador = new KafkaConsumerConfig().kafkaErrorHandler(org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class), props);
        DefaultErrorHandler d = assertInstanceOf(DefaultErrorHandler.class, manejador);

        // removeClassification devuelve la clasificación registrada: false = no se reintenta, null = sin regla (se reintenta)
        assertEquals(Boolean.FALSE, d.removeClassification(EventoInvalidoException.class));
        assertEquals(Boolean.FALSE, d.removeClassification(DeserializationException.class));
    }

    @Test
    void unErrorPasajeroDeLaBaseSiSeReintenta() {
        DefaultErrorHandler d = (DefaultErrorHandler) new KafkaConsumerConfig().kafkaErrorHandler(org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class), props);

        assertNull(d.removeClassification(TransientDataAccessResourceException.class));
    }
}
