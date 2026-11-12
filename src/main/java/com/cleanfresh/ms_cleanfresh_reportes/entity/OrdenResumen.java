package com.cleanfresh.ms_cleanfresh_reportes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Lo último que se sabe de una orden, reconstruido a partir de sus eventos de Kafka (Spec 044). Es lo que leen los reportes: cuándo
 * se creó, cuándo se entregó, en qué estado está, por cuánto y en qué sucursal.
 */
@Entity
@Table(name = "orden_resumen")
public class OrdenResumen {

    @Id
    @Column(name = "numero_orden", length = 40)
    private String numeroOrden;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(length = 100)
    private String sucursal;

    @Column(length = 150)
    private String servicio;

    private double total;

    @Column(name = "creada_en")
    private Instant creadaEn;

    @Column(name = "entregada_en")
    private Instant entregadaEn;

    @Column(name = "actualizada_en")
    private Instant actualizadaEn;

    protected OrdenResumen() {
    }

    public OrdenResumen(String numeroOrden) {
        this.numeroOrden = numeroOrden;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getSucursal() {
        return sucursal;
    }

    public void setSucursal(String sucursal) {
        this.sucursal = sucursal;
    }

    public String getServicio() {
        return servicio;
    }

    public void setServicio(String servicio) {
        this.servicio = servicio;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(Instant creadaEn) {
        this.creadaEn = creadaEn;
    }

    public Instant getEntregadaEn() {
        return entregadaEn;
    }

    public void setEntregadaEn(Instant entregadaEn) {
        this.entregadaEn = entregadaEn;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }

    public void setActualizadaEn(Instant actualizadaEn) {
        this.actualizadaEn = actualizadaEn;
    }
}
