package com.cleanfresh.ms_cleanfresh_reportes;

import com.cleanfresh.ms_cleanfresh_reportes.entity.OrdenResumen;
import com.cleanfresh.ms_cleanfresh_reportes.messaging.EventoInvalidoException;
import com.cleanfresh.ms_cleanfresh_reportes.messaging.OrderEventMessage;
import com.cleanfresh.ms_cleanfresh_reportes.repository.EventoProcesadoRepository;
import com.cleanfresh.ms_cleanfresh_reportes.repository.OrdenResumenRepository;
import com.cleanfresh.ms_cleanfresh_reportes.service.EventoOrdenService;
import com.cleanfresh.ms_cleanfresh_reportes.service.EventoOrdenService.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Spec 044: la lógica de los eventos de órdenes, sin Kafka (valida, descarta repetidos y deja al día el resumen). */
@SpringBootTest
class EventoOrdenServiceTests {

    @Autowired
    EventoOrdenService servicio;
    @Autowired
    OrdenResumenRepository ordenes;
    @Autowired
    EventoProcesadoRepository procesados;

    @BeforeEach
    void limpiar() {
        procesados.deleteAll();
        ordenes.deleteAll();
    }

    static OrderEventMessage evento(String id, String tipo, String estado, String timestamp) {
        return new OrderEventMessage(tipo, id, timestamp, "traza-1", "ORD-0001",
                new OrderEventMessage.Payload("ORD-0001", "cliente-uuid", "cliente@cleanfresh.com", "Planchado", "Providencia",
                        9500.0, estado, "2026-10-15", "2026-10-15T14:00:00Z", "op-1"));
    }

    @Test
    void elPrimerEventoCreaElResumenDeLaOrden() {
        Resultado r = servicio.procesar(evento("e1", "OrderCreated", "CREADO", "2026-10-15T14:00:01Z"));

        assertEquals(Resultado.PROCESADO, r);
        OrdenResumen o = ordenes.findById("ORD-0001").orElseThrow();
        assertEquals("CREADO", o.getEstado());
        assertEquals("Providencia", o.getSucursal());
        assertEquals("Planchado", o.getServicio());
        assertEquals(9500.0, o.getTotal());
        assertEquals(Instant.parse("2026-10-15T14:00:00Z"), o.getCreadaEn());
        assertNull(o.getEntregadaEn());
        assertNotNull(procesados.findById("e1").orElse(null));
    }

    @Test
    void unEventoConElMismoEventIdNoSeProcesaDosVeces() {
        servicio.procesar(evento("e1", "OrderCreated", "CREADO", "2026-10-15T14:00:01Z"));

        Resultado r = servicio.procesar(evento("e1", "OrderCreated", "CREADO", "2026-10-15T14:00:01Z"));

        assertEquals(Resultado.DUPLICADO, r);
        assertEquals(1, ordenes.count());
        assertEquals(1, procesados.count());
    }

    @Test
    void loscambiosDeEstadoActualizanLaOrdenYLaEntregaGuardaSuMomento() {
        servicio.procesar(evento("e1", "OrderCreated", "CREADO", "2026-10-15T14:00:01Z"));
        servicio.procesar(evento("e2", "OrderAccepted", "ACEPTADO", "2026-10-15T14:05:00Z"));
        servicio.procesar(evento("e3", "OrderDelivered", "ENTREGADO", "2026-10-15T15:00:00Z"));

        OrdenResumen o = ordenes.findById("ORD-0001").orElseThrow();
        assertEquals("ENTREGADO", o.getEstado());
        assertEquals(Instant.parse("2026-10-15T15:00:00Z"), o.getEntregadaEn());
        // la fecha de creación no se pisa con los eventos siguientes
        assertEquals(Instant.parse("2026-10-15T14:00:00Z"), o.getCreadaEn());
    }

    @Test
    void unEventoViejoNoPisaElEstadoMasNuevo() {
        servicio.procesar(evento("e2", "OrderAccepted", "ACEPTADO", "2026-10-15T14:05:00Z"));

        // llega tarde un evento anterior (p. ej. al releer desde el inicio)
        servicio.procesar(evento("e1", "OrderCreated", "CREADO", "2026-10-15T14:00:01Z"));

        assertEquals("ACEPTADO", ordenes.findById("ORD-0001").orElseThrow().getEstado());
        assertEquals(2, procesados.count());
    }

    @Test
    void sinFechaHoraLaCreacionEsElMomentoDelPrimerEvento() {
        OrderEventMessage sinFecha = new OrderEventMessage("OrderAccepted", "e9", "2026-10-15T14:05:00Z", "t", "ORD-0002",
                new OrderEventMessage.Payload("ORD-0002", "c", null, "Planchado", "Maipu", 9500.0, "ACEPTADO", "2026-10-15", null, null));

        servicio.procesar(sinFecha);

        assertEquals(Instant.parse("2026-10-15T14:05:00Z"), ordenes.findById("ORD-0002").orElseThrow().getCreadaEn());
    }

    @Test
    void losEventosQueIncumplenElContratoSeRechazanSinGuardarNada() {
        assertThrows(EventoInvalidoException.class, () -> servicio.procesar(null));
        assertThrows(EventoInvalidoException.class, () -> servicio.procesar(
                new OrderEventMessage("OrderCreated", "x", "2026-10-15T14:00:00Z", "t", "ORD-1", null)));
        assertThrows(EventoInvalidoException.class,
                () -> servicio.procesar(evento("", "OrderCreated", "CREADO", "2026-10-15T14:00:00Z")));
        assertThrows(EventoInvalidoException.class,
                () -> servicio.procesar(evento("x", "OrderInventado", "CREADO", "2026-10-15T14:00:00Z")));
        assertThrows(EventoInvalidoException.class,
                () -> servicio.procesar(evento("x", "OrderCreated", "", "2026-10-15T14:00:00Z")));
        assertThrows(EventoInvalidoException.class,
                () -> servicio.procesar(evento("x", "OrderCreated", "CREADO", "ayer")));
        assertThrows(EventoInvalidoException.class,
                () -> servicio.procesar(evento("x", "OrderCreated", "CREADO", null)));
        assertEquals(0, ordenes.count());
        assertEquals(0, procesados.count());
    }

    @Test
    void unTotalNegativoOAusenteEsInvalido() {
        OrderEventMessage negativo = new OrderEventMessage("OrderCreated", "x", "2026-10-15T14:00:00Z", "t", "ORD-1",
                new OrderEventMessage.Payload("ORD-1", "c", null, "Planchado", "Maipu", -5.0, "CREADO", null, null, null));
        OrderEventMessage sinTotal = new OrderEventMessage("OrderCreated", "y", "2026-10-15T14:00:00Z", "t", "ORD-1",
                new OrderEventMessage.Payload("ORD-1", "c", null, "Planchado", "Maipu", null, "CREADO", null, null, null));

        assertThrows(EventoInvalidoException.class, () -> servicio.procesar(negativo));
        assertThrows(EventoInvalidoException.class, () -> servicio.procesar(sinTotal));
    }

    @Test
    void unaFechaHoraIlegibleEsInvalida() {
        OrderEventMessage malaFecha = new OrderEventMessage("OrderCreated", "z", "2026-10-15T14:00:00Z", "t", "ORD-1",
                new OrderEventMessage.Payload("ORD-1", "c", null, "Planchado", "Maipu", 100.0, "CREADO", null, "no-es-fecha", null));

        assertThrows(EventoInvalidoException.class, () -> servicio.procesar(malaFecha));
    }
}
