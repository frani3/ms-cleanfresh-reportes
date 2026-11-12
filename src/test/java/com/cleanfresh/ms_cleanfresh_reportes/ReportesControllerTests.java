package com.cleanfresh.ms_cleanfresh_reportes;

import com.cleanfresh.ms_cleanfresh_reportes.controller.ErroresHandler;
import com.cleanfresh.ms_cleanfresh_reportes.controller.ReportesController;
import com.cleanfresh.ms_cleanfresh_reportes.dto.BranchReport;
import com.cleanfresh.ms_cleanfresh_reportes.dto.Kpis;
import com.cleanfresh.ms_cleanfresh_reportes.dto.LeadTime;
import com.cleanfresh.ms_cleanfresh_reportes.dto.TopServicio;
import com.cleanfresh.ms_cleanfresh_reportes.dto.VentaHora;
import com.cleanfresh.ms_cleanfresh_reportes.service.Rango;
import com.cleanfresh.ms_cleanfresh_reportes.service.ReporteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportesControllerTests {

    private final ReporteService servicio = mock(ReporteService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ReportesController(servicio)).setControllerAdvice(new ErroresHandler()).build();
    }

    @Test
    void porSucursalConservaLaFormaQueUsaElPanelAdmin() throws Exception {
        when(servicio.porSucursal(Rango.LAST30D)).thenReturn(List.of(new BranchReport("Providencia", 42)));

        mvc.perform(get("/api/reportes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branch").value("Providencia"))
                .andExpect(jsonPath("$[0].orders").value(42));
    }

    @Test
    void kpisUsaLas24HorasPorDefectoYAceptaOtroRango() throws Exception {
        Kpis vacio = new Kpis("last24h", Instant.EPOCH, Instant.EPOCH, 0, 0, 0, 0, 0, 0, new LeadTime(null, null, null, 0),
                Map.of());
        when(servicio.kpis(Rango.LAST24H)).thenReturn(vacio);
        when(servicio.kpis(Rango.LAST7D)).thenReturn(vacio);

        mvc.perform(get("/api/reportes/kpis")).andExpect(status().isOk()).andExpect(jsonPath("$.leadTime.muestras").value(0));
        mvc.perform(get("/api/reportes/kpis").param("range", "last7d")).andExpect(status().isOk());

        verify(servicio).kpis(Rango.LAST24H);
        verify(servicio).kpis(Rango.LAST7D);
    }

    @Test
    void unRangoInexistenteEs400() throws Exception {
        mvc.perform(get("/api/reportes/kpis").param("range", "ayer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("range inválido: ayer (use last1h, last24h, last7d o last30d)"));
    }

    @Test
    void ventasPorHoraDevuelveLaSerie() throws Exception {
        when(servicio.ventasPorHora(Rango.LAST24H)).thenReturn(List.of(new VentaHora(Instant.parse("2026-10-15T14:00:00Z"), 2, 10000.0)));

        mvc.perform(get("/api/reportes/ventas-por-hora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pedidos").value(2))
                .andExpect(jsonPath("$[0].monto").value(10000.0));
    }

    @Test
    void topServiciosUsa7DiasYCincoPorDefecto() throws Exception {
        when(servicio.topServicios(Rango.LAST7D, 5)).thenReturn(List.of(new TopServicio("Planchado", 3, 28500.0)));

        mvc.perform(get("/api/reportes/top-servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].servicio").value("Planchado"));
        mvc.perform(get("/api/reportes/top-servicios").param("limite", "2").param("range", "last30d")).andExpect(status().isOk());
        verify(servicio).topServicios(Rango.LAST30D, 2);
    }
}
