package com.cleanfresh.ms_cleanfresh_reportes;

import com.cleanfresh.ms_cleanfresh_reportes.dto.BranchReport;
import com.cleanfresh.ms_cleanfresh_reportes.dto.Kpis;
import com.cleanfresh.ms_cleanfresh_reportes.dto.TopServicio;
import com.cleanfresh.ms_cleanfresh_reportes.dto.VentaHora;
import com.cleanfresh.ms_cleanfresh_reportes.entity.OrdenResumen;
import com.cleanfresh.ms_cleanfresh_reportes.repository.OrdenResumenRepository;
import com.cleanfresh.ms_cleanfresh_reportes.service.Rango;
import com.cleanfresh.ms_cleanfresh_reportes.service.ReporteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 044: KPIs, ventas por hora, lead time, estados activos y top de servicios, con "ahora" fijo. */
@SpringBootTest
class ReporteServiceTests {

    static final Instant AHORA = Instant.parse("2026-10-15T15:30:00Z");

    @Autowired
    OrdenResumenRepository ordenes;

    ReporteService reportes;

    @BeforeEach
    void preparar() {
        ordenes.deleteAll();
        reportes = new ReporteService(ordenes, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void orden(String numero, String estado, String servicio, String sucursal, double total, String creada, String entregada) {
        OrdenResumen o = new OrdenResumen(numero);
        o.setEstado(estado);
        o.setServicio(servicio);
        o.setSucursal(sucursal);
        o.setTotal(total);
        o.setCreadaEn(Instant.parse(creada));
        o.setEntregadaEn(entregada == null ? null : Instant.parse(entregada));
        o.setActualizadaEn(Instant.parse(creada));
        ordenes.save(o);
    }

    @Test
    void sinOrdenesTodoEsCeroYElLeadTimeNoTieneMuestras() {
        Kpis k = reportes.kpis(Rango.LAST24H);

        assertEquals(0, k.pedidos());
        assertEquals(0.0, k.ventas());
        assertEquals(0.0, k.ticketPromedio());
        assertEquals(0, k.leadTime().muestras());
        assertNull(k.leadTime().promedioMin());
        assertEquals(Map.of("CREADO", 0L, "ACEPTADO", 0L, "EN_PREPARACION", 0L, "DESPACHADO", 0L), k.estadosActivos());
    }

    @Test
    void lasVentasNoIncluyenLosPedidosCancelados() {
        orden("ORD-1", "ENTREGADO", "Planchado", "Providencia", 9500, "2026-10-15T14:00:00Z", "2026-10-15T14:40:00Z");
        orden("ORD-2", "CREADO", "Lavado en seco", "Providencia", 25000, "2026-10-15T15:00:00Z", null);
        orden("ORD-3", "CANCELADO", "Planchado", "Maipu", 9500, "2026-10-15T15:10:00Z", null);

        Kpis k = reportes.kpis(Rango.LAST24H);

        assertEquals(3, k.pedidos());
        assertEquals(2, k.pedidosNoCancelados());
        assertEquals(34500.0, k.ventas());
        assertEquals(17250.0, k.ticketPromedio());
        assertEquals(1, k.entregados());
        assertEquals(1, k.cancelados());
    }

    @Test
    void elLeadTimeEsElTiempoEntreCrearYEntregarEnMinutos() {
        orden("ORD-1", "ENTREGADO", "Planchado", "Providencia", 9500, "2026-10-15T14:00:00Z", "2026-10-15T14:40:00Z");
        orden("ORD-2", "ENTREGADO", "Planchado", "Providencia", 9500, "2026-10-15T14:00:00Z", "2026-10-15T15:20:00Z");

        var lead = reportes.kpis(Rango.LAST24H).leadTime();

        assertEquals(2, lead.muestras());
        assertEquals(60.0, lead.promedioMin());
        assertEquals(40.0, lead.minimoMin());
        assertEquals(80.0, lead.maximoMin());
    }

    @Test
    void losEstadosActivosCuentanLasOrdenesEnCursoSinImportarCuandoSeCrearon() {
        orden("ORD-1", "CREADO", "Planchado", "Providencia", 9500, "2026-10-01T10:00:00Z", null);
        orden("ORD-2", "EN_PREPARACION", "Planchado", "Providencia", 9500, "2026-10-15T14:00:00Z", null);
        orden("ORD-3", "EN_PREPARACION", "Planchado", "Maipu", 9500, "2026-10-15T14:10:00Z", null);
        orden("ORD-4", "ENTREGADO", "Planchado", "Maipu", 9500, "2026-10-15T14:10:00Z", "2026-10-15T15:00:00Z");

        Map<String, Long> activos = reportes.kpis(Rango.LAST24H).estadosActivos();

        assertEquals(1L, activos.get("CREADO"));
        assertEquals(2L, activos.get("EN_PREPARACION"));
        assertEquals(0L, activos.get("DESPACHADO"));
    }

    @Test
    void ventasPorHoraTraeUnaFilaPorHoraConCerosDondeNoHuboPedidos() {
        orden("ORD-1", "CREADO", "Planchado", "Providencia", 9500, "2026-10-15T14:10:00Z", null);
        orden("ORD-2", "CREADO", "Planchado", "Providencia", 500, "2026-10-15T14:50:00Z", null);
        orden("ORD-3", "CANCELADO", "Planchado", "Providencia", 777, "2026-10-15T14:55:00Z", null);

        List<VentaHora> horas = reportes.ventasPorHora(Rango.LAST1H);

        // el rango de 1 hora que termina a las 15:30 toca las horas 14:00 (desde 14:30) y 15:00
        assertEquals(List.of(Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T15:00:00Z")),
                horas.stream().map(VentaHora::hora).toList());
        assertEquals(1, horas.get(0).pedidos());
        assertEquals(500.0, horas.get(0).monto());
        assertEquals(0, horas.get(1).pedidos());
    }

    @Test
    void ventasPorHoraAgrupaPorHoraDeCreacion() {
        orden("ORD-1", "CREADO", "Planchado", "Providencia", 9500, "2026-10-15T14:10:00Z", null);
        orden("ORD-2", "CREADO", "Planchado", "Providencia", 500, "2026-10-15T14:50:00Z", null);
        orden("ORD-3", "CREADO", "Planchado", "Providencia", 100, "2026-10-15T15:05:00Z", null);

        List<VentaHora> horas = reportes.ventasPorHora(Rango.LAST24H);

        VentaHora catorce = horas.stream().filter(h -> h.hora().equals(Instant.parse("2026-10-15T14:00:00Z"))).findFirst().orElseThrow();
        assertEquals(2, catorce.pedidos());
        assertEquals(10000.0, catorce.monto());
        assertEquals(25, horas.size());
        assertTrue(horas.get(0).hora().isBefore(horas.get(horas.size() - 1).hora()));
    }

    @Test
    void elTopDeServiciosOrdenaPorPedidosYLuegoPorMonto() {
        orden("ORD-1", "CREADO", "Planchado", "Providencia", 9500, "2026-10-14T10:00:00Z", null);
        orden("ORD-2", "CREADO", "Planchado", "Maipu", 9500, "2026-10-14T11:00:00Z", null);
        orden("ORD-3", "CREADO", "Lavado en seco", "Maipu", 25000, "2026-10-14T12:00:00Z", null);
        orden("ORD-4", "CREADO", "Lavado de edredones", "Maipu", 32000, "2026-10-14T12:00:00Z", null);
        orden("ORD-5", "CANCELADO", "Lavado de edredones", "Maipu", 32000, "2026-10-14T12:30:00Z", null);

        List<TopServicio> top = reportes.topServicios(Rango.LAST7D, 5);

        assertEquals("Planchado", top.get(0).servicio());
        assertEquals(2, top.get(0).pedidos());
        assertEquals(19000.0, top.get(0).monto());
        // empatan en 1 pedido: primero el de más monto; el cancelado no cuenta
        assertEquals("Lavado de edredones", top.get(1).servicio());
        assertEquals(1, top.get(1).pedidos());
        assertEquals("Lavado en seco", top.get(2).servicio());
    }

    @Test
    void elTopRespetaElLimite() {
        orden("ORD-1", "CREADO", "A", "Providencia", 1, "2026-10-14T10:00:00Z", null);
        orden("ORD-2", "CREADO", "B", "Providencia", 2, "2026-10-14T10:00:00Z", null);
        orden("ORD-3", "CREADO", "C", "Providencia", 3, "2026-10-14T10:00:00Z", null);

        assertEquals(2, reportes.topServicios(Rango.LAST7D, 2).size());
        assertEquals(1, reportes.topServicios(Rango.LAST7D, 0).size());
    }

    @Test
    void elRangoDejaFueraLoAnterior() {
        orden("ORD-1", "CREADO", "Planchado", "Providencia", 9500, "2026-10-15T14:00:00Z", null);
        orden("ORD-2", "CREADO", "Planchado", "Providencia", 9500, "2026-10-10T14:00:00Z", null);

        assertEquals(1, reportes.kpis(Rango.LAST24H).pedidos());
        assertEquals(2, reportes.kpis(Rango.LAST7D).pedidos());
        assertEquals(0, reportes.kpis(Rango.LAST1H).pedidos());
    }

    @Test
    void porSucursalCuentaLosPedidosNoCanceladosYOrdenaDeMayorAMenor() {
        orden("ORD-1", "CREADO", "Planchado", "Providencia", 100, "2026-10-14T10:00:00Z", null);
        orden("ORD-2", "CREADO", "Planchado", "Maipu", 100, "2026-10-14T10:00:00Z", null);
        orden("ORD-3", "CREADO", "Planchado", "Maipu", 100, "2026-10-14T10:00:00Z", null);
        orden("ORD-4", "CANCELADO", "Planchado", "Providencia", 100, "2026-10-14T10:00:00Z", null);

        assertEquals(List.of(new BranchReport("Maipu", 2), new BranchReport("Providencia", 1)), reportes.porSucursal(Rango.LAST7D));
    }

    @Test
    void elRangoSeLeeDelTextoYUnoDesconocidoEsInvalido() {
        assertEquals(Rango.LAST7D, Rango.de("LAST7D", Rango.LAST24H));
        assertEquals(Rango.LAST24H, Rango.de(null, Rango.LAST24H));
        assertEquals(Rango.LAST30D, Rango.de("  ", Rango.LAST30D));
        assertThrows(IllegalArgumentException.class, () -> Rango.de("ayer", Rango.LAST24H));
    }
}
