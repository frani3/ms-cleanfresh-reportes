package com.cleanfresh.ms_cleanfresh_reportes.messaging;

/**
 * Evento de negocio que publica {@code orders} en {@code orders.events} (Spec 044). Es el contrato con ese servicio, que define su
 * propia copia de este record: los dos lados solo comparten el JSON, no clases.
 *
 * @param type          qué pasó: OrderCreated, OrderAccepted, OrderPreparing, OrderDispatched, OrderDelivered u OrderCancelled
 * @param eventId       identificador único del evento; es la clave de idempotencia
 * @param timestamp     cuándo ocurrió (ISO-8601, UTC)
 * @param traceId       agrupa lo que se dispara desde una misma operación
 * @param correlationId el número de orden
 * @param payload       los datos de la orden en ese momento
 */
public record OrderEventMessage(
        String type,
        String eventId,
        String timestamp,
        String traceId,
        String correlationId,
        Payload payload
) {

    /** {@code estado} es el estado después del evento; {@code fechaHora} es cuándo se creó la orden (null en las antiguas). */
    public record Payload(
            String numeroOrden,
            String cliente,
            String clienteNombre,
            String servicio,
            String sucursal,
            Double total,
            String estado,
            String fecha,
            String fechaHora,
            String actor
    ) {
    }
}
