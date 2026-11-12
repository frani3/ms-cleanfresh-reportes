package com.cleanfresh.ms_cleanfresh_reportes.controller;

import com.cleanfresh.ms_cleanfresh_reportes.dto.BranchReport;
import com.cleanfresh.ms_cleanfresh_reportes.dto.Kpis;
import com.cleanfresh.ms_cleanfresh_reportes.dto.TopServicio;
import com.cleanfresh.ms_cleanfresh_reportes.dto.VentaHora;
import com.cleanfresh.ms_cleanfresh_reportes.service.Rango;
import com.cleanfresh.ms_cleanfresh_reportes.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reportes en tiempo casi real (Spec 044): se calculan con los eventos que {@code orders} publica en Kafka, sin consultar a
 * {@code orders}. Solo lectura. Se llega únicamente por el BFF (rol Admin).
 */
@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Reportes", description = "KPIs, ventas por hora, lead time, estados activos y servicios más pedidos, calculados con los eventos de Kafka. Solo lectura.")
public class ReportesController {

    private static final String RANGO = "Ventana de tiempo: last1h, last24h, last7d o last30d";

    private final ReporteService servicio;

    public ReportesController(ReporteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Pedidos por sucursal (no cancelados) en el rango; por defecto los últimos 30 días")
    public List<BranchReport> ordenesPorSucursal(@Parameter(description = RANGO) @RequestParam(required = false) String range) {
        return servicio.porSucursal(Rango.de(range, Rango.LAST30D));
    }

    @GetMapping("/kpis")
    @Operation(summary = "Indicadores del rango: pedidos, ventas, ticket promedio, entregados, cancelados, lead time y estados activos; por defecto las últimas 24 horas")
    public Kpis kpis(@Parameter(description = RANGO) @RequestParam(required = false) String range) {
        return servicio.kpis(Rango.de(range, Rango.LAST24H));
    }

    @GetMapping("/ventas-por-hora")
    @Operation(summary = "Pedidos y monto por cada hora del rango (con ceros donde no hubo); por defecto las últimas 24 horas")
    public List<VentaHora> ventasPorHora(@Parameter(description = RANGO) @RequestParam(required = false) String range) {
        return servicio.ventasPorHora(Rango.de(range, Rango.LAST24H));
    }

    @GetMapping("/top-servicios")
    @Operation(summary = "Servicios más pedidos del rango; por defecto los últimos 7 días y 5 servicios")
    public List<TopServicio> topServicios(@Parameter(description = RANGO) @RequestParam(required = false) String range,
                                          @RequestParam(defaultValue = "5") int limite) {
        return servicio.topServicios(Rango.de(range, Rango.LAST7D), limite);
    }
}
