package com.cleanfresh.ms_cleanfresh_reportes.controller;

import com.cleanfresh.ms_cleanfresh_reportes.dto.BranchReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Esqueleto de EP2: devuelve una respuesta fija, con la misma forma de datos
 * que hoy usa la pestaña "Analítica por sucursal" del panel Admin. El cálculo
 * real a partir de las órdenes llega en la siguiente entrega.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    @GetMapping
    public List<BranchReport> ordenesPorSucursal() {
        return List.of(
                new BranchReport("Providencia", 42),
                new BranchReport("Ñuñoa", 35),
                new BranchReport("Las Condes", 28),
                new BranchReport("Maipú", 19)
        );
    }
}
