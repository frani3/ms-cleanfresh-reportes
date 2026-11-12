package com.cleanfresh.ms_cleanfresh_reportes.config;

import com.cleanfresh.ms_cleanfresh_reportes.messaging.EventoInvalidoException;
import com.cleanfresh.ms_cleanfresh_reportes.messaging.OrderEventMessage;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejo de errores del consumidor (Spec 044). Un mensaje que no se puede procesar nunca detiene el consumo de los demás:
 *
 * <ul>
 *   <li><b>No reintentable</b> (JSON roto, contrato incumplido): va directo a su DLT.</li>
 *   <li><b>Pasajero</b> (la base cae un momento): se reintenta con espera creciente y, si sigue fallando, va al DLT.</li>
 *   <li>El <b>DLT</b> es {@code <tópico>.<consumidor>.DLT} y guarda el mensaje original más las cabeceras {@code kafka_dlt-*} con
 *       la excepción, su stack, el tópico, la partición y el offset de origen y el grupo que lo recibió.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AppKafkaProperties.class)
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /**
     * Productor solo para los DLT. Envía el valor tal como llegó: bytes si no se pudo leer (JSON roto) o el mensaje ya tipado si
     * falló después.
     */
    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate(@Value("${spring.kafka.bootstrap-servers}") String brokers) {
        Map<String, Object> config = new HashMap<>();
        config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokers);
        config.put(CommonClientConfigs.CLIENT_ID_CONFIG, "ms-cleanfresh-reportes-dlt");
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30_000);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
        JacksonJsonSerializer<OrderEventMessage> json = new JacksonJsonSerializer<>();
        json.setAddTypeInfo(false);
        DelegatingByTypeSerializer valor = new DelegatingByTypeSerializer(
                Map.of(byte[].class, new ByteArraySerializer(), OrderEventMessage.class, json), true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config, new StringSerializer(), valor));
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dltKafkaTemplate, AppKafkaProperties props) {
        DeadLetterPublishingRecoverer publicador = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (registro, error) -> new TopicPartition(props.dlt(registro.topic()), registro.partition()));
        // El registro del fallo lleva el contexto necesario para investigarlo sin ir a buscarlo al DLT.
        ConsumerRecordRecoverer recuperador = (registro, error) -> {
            log.error("Mensaje NO PROCESADO de {} (partición {}, offset {}, clave {}) va a {}: {}", registro.topic(),
                    registro.partition(), registro.offset(), registro.key(), props.dlt(registro.topic()), causa(error).toString());
            publicador.accept(registro, error);
        };

        ExponentialBackOffWithMaxRetries espera = new ExponentialBackOffWithMaxRetries(props.reintentos());
        espera.setInitialInterval(props.esperaInicialMs());
        espera.setMultiplier(2.0);
        espera.setMaxInterval(props.esperaMaximaMs());

        DefaultErrorHandler manejador = new DefaultErrorHandler(recuperador, espera);
        // JSON roto y similares ya son no reintentables por defecto; un evento que incumple el contrato tampoco se arregla reintentando.
        manejador.addNotRetryableExceptions(EventoInvalidoException.class);
        manejador.setRetryListeners((registro, error, intento) -> log.warn(
                "Falló el procesamiento de {} (partición {}, offset {}), intento {}: {}", registro.topic(), registro.partition(),
                registro.offset(), intento, causa(error).toString()));
        return manejador;
    }

    private static Throwable causa(Throwable error) {
        Throwable actual = error;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        return actual;
    }
}
