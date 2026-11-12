package com.cleanfresh.ms_cleanfresh_reportes.service;

import com.cleanfresh.ms_cleanfresh_reportes.dto.BranchReport;
import com.cleanfresh.ms_cleanfresh_reportes.dto.Kpis;
import com.cleanfresh.ms_cleanfresh_reportes.dto.LeadTime;
import com.cleanfresh.ms_cleanfresh_reportes.dto.TopServicio;
import com.cleanfresh.ms_cleanfresh_reportes.dto.VentaHora;
import com.cleanfresh.ms_cleanfresh_reportes.entity.OrdenResumen;
import com.cleanfresh.ms_cleanfresh_reportes.repository.OrdenResumenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Calcula los reportes a partir del resumen de órdenes que mantiene {@link EventoOrdenService} con los eventos de Kafka (Spec 044).
 * Solo lee: nunca habla con {@code orders} ni lo bloquea. Las agrupaciones se hacen en memoria, porque el volumen de una red de
 * lavanderías (órdenes por hora, no por segundo) lo permite y así el cálculo no depende del dialecto de la base.
 */
@Service
@Transactional(readOnly = true)
public class ReporteService {

    /** Estados en los que una orden sigue en curso. */
    static final List<String> ESTADOS_ACTIVOS = List.of("CREADO", "ACEPTADO", "EN_PREPARACION", "DESPACHADO");

    private final OrdenResumenRepository ordenes;
    private final Clock reloj;

    public ReporteService(OrdenResumenRepository ordenes, Clock reloj) {
        this.ordenes = ordenes;
        this.reloj = reloj;
    }

    public Kpis kpis(Rango rango) {
        Instant hasta = reloj.instant();
        Instant desde = hasta.minus(rango.duracion());
        List<OrdenResumen> creadas = ordenes.findByCreadaEnGreaterThanEqual(desde);
        List<OrdenResumen> noCanceladas = creadas.stream().filter(o -> !esCancelada(o)).toList();
        double ventas = noCanceladas.stream().mapToDouble(OrdenResumen::getTotal).sum();

        long entregados = creadas.stream().filter(o -> "ENTREGADO".equals(o.getEstado())).count();
        long cancelados = (long) creadas.size() - noCanceladas.size();
        return new Kpis(rango.clave(), desde, hasta, creadas.size(), noCanceladas.size(), ventas,
                noCanceladas.isEmpty() ? 0 : ventas / noCanceladas.size(), entregados, cancelados,
                leadTime(desde), estadosActivos());
    }

    /** Una fila por cada hora del rango (con ceros donde no hubo pedidos), de la más antigua a la más reciente. */
    public List<VentaHora> ventasPorHora(Rango rango) {
        Instant hasta = reloj.instant();
        Instant desde = hasta.minus(rango.duracion());
        Map<Instant, long[]> horas = new TreeMap<>();
        for (Instant h = desde.truncatedTo(ChronoUnit.HOURS); !h.isAfter(hasta); h = h.plus(1, ChronoUnit.HOURS)) {
            horas.put(h, new long[]{0, 0});
        }
        Map<Instant, Double> monto = new TreeMap<>();
        for (OrdenResumen o : ordenes.findByCreadaEnGreaterThanEqual(desde)) {
            if (esCancelada(o) || o.getCreadaEn() == null) {
                continue;
            }
            Instant hora = o.getCreadaEn().truncatedTo(ChronoUnit.HOURS);
            horas.computeIfAbsent(hora, k -> new long[]{0, 0})[0]++;
            monto.merge(hora, o.getTotal(), Double::sum);
        }
        List<VentaHora> resultado = new ArrayList<>();
        horas.forEach((hora, cuenta) -> resultado.add(new VentaHora(hora, cuenta[0], monto.getOrDefault(hora, 0.0))));
        return resultado;
    }

    /** Los servicios más pedidos del rango (por cantidad de pedidos y, si empatan, por monto). */
    public List<TopServicio> topServicios(Rango rango, int limite) {
        Instant desde = reloj.instant().minus(rango.duracion());
        Map<String, List<OrdenResumen>> porServicio = ordenes.findByCreadaEnGreaterThanEqual(desde).stream()
                .filter(o -> !esCancelada(o) && o.getServicio() != null)
                .collect(Collectors.groupingBy(OrdenResumen::getServicio));
        return porServicio.entrySet().stream()
                .map(e -> new TopServicio(e.getKey(), e.getValue().size(), e.getValue().stream().mapToDouble(OrdenResumen::getTotal).sum()))
                .sorted(Comparator.comparingLong(TopServicio::pedidos).reversed()
                        .thenComparing(Comparator.comparingDouble(TopServicio::monto).reversed())
                        .thenComparing(TopServicio::servicio))
                .limit(Math.max(1, limite))
                .toList();
    }

    /** Pedidos (no cancelados) por sucursal en el rango; es lo que muestra "Analítica por sucursal" del panel Admin. */
    public List<BranchReport> porSucursal(Rango rango) {
        Instant desde = reloj.instant().minus(rango.duracion());
        Map<String, Long> cuenta = ordenes.findByCreadaEnGreaterThanEqual(desde).stream()
                .filter(o -> !esCancelada(o) && o.getSucursal() != null)
                .collect(Collectors.groupingBy(OrdenResumen::getSucursal, Collectors.counting()));
        return cuenta.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new BranchReport(e.getKey(), e.getValue().intValue()))
                .toList();
    }

    private LeadTime leadTime(Instant desde) {
        List<Double> minutos = ordenes.findByEntregadaEnGreaterThanEqual(desde).stream()
                .filter(o -> o.getCreadaEn() != null && o.getEntregadaEn() != null)
                .map(o -> Duration.between(o.getCreadaEn(), o.getEntregadaEn()).toMillis() / 60000.0)
                .toList();
        if (minutos.isEmpty()) {
            return new LeadTime(null, null, null, 0);
        }
        return new LeadTime(redondear(minutos.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                redondear(minutos.stream().mapToDouble(Double::doubleValue).min().orElse(0)),
                redondear(minutos.stream().mapToDouble(Double::doubleValue).max().orElse(0)), minutos.size());
    }

    private Map<String, Long> estadosActivos() {
        Map<String, Long> cuenta = new LinkedHashMap<>();
        ESTADOS_ACTIVOS.forEach(e -> cuenta.put(e, 0L));
        ordenes.findByEstadoIn(ESTADOS_ACTIVOS).forEach(o -> cuenta.merge(o.getEstado(), 1L, Long::sum));
        return cuenta;
    }

    private static boolean esCancelada(OrdenResumen o) {
        return "CANCELADO".equals(o.getEstado());
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
