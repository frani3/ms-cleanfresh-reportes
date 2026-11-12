package com.cleanfresh.ms_cleanfresh_reportes.dto;

/** Órdenes por sucursal; mantiene la forma que ya usaba el panel Admin ({@code branch}, {@code orders}). */
public record BranchReport(String branch, int orders) {
}
