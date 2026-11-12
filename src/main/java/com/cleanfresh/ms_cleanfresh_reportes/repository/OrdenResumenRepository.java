package com.cleanfresh.ms_cleanfresh_reportes.repository;

import com.cleanfresh.ms_cleanfresh_reportes.entity.OrdenResumen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OrdenResumenRepository extends JpaRepository<OrdenResumen, String> {

    /** Órdenes creadas desde un instante (las ventas del rango). */
    List<OrdenResumen> findByCreadaEnGreaterThanEqual(Instant desde);

    /** Órdenes entregadas desde un instante (para el lead time). */
    List<OrdenResumen> findByEntregadaEnGreaterThanEqual(Instant desde);

    /** Órdenes que están hoy en alguno de esos estados (los estados activos, sin importar cuándo se crearon). */
    List<OrdenResumen> findByEstadoIn(Collection<String> estados);
}
