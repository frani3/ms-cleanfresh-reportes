package com.cleanfresh.ms_cleanfresh_reportes;

import com.cleanfresh.ms_cleanfresh_reportes.controller.ReportesController;
import com.cleanfresh.ms_cleanfresh_reportes.dto.BranchReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportesControllerTests {

    @Test
    void devuelveLasCuatroSucursalesConSusOrdenes() {
        List<BranchReport> reporte = new ReportesController().ordenesPorSucursal();

        assertEquals(4, reporte.size());
        assertEquals(new BranchReport("Providencia", 42), reporte.get(0));
        assertEquals(new BranchReport("Maipú", 19), reporte.get(3));
    }
}
