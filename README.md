# ms-cleanfresh-reportes

Microservicio de reportes de Clean&Fresh Manager. Spring Boot 4.1.1 / Java 21, puerto **8084**. Calcula KPIs, ventas por hora, lead time, estados
activos y servicios más pedidos **a partir de los eventos que `orders` publica en Kafka** (`orders.events`), sin consultar nunca a `orders`: leer
reportes no le quita capacidad al flujo de pedidos. Guarda el resultado en PostgreSQL (base `reportes_db`).

Se consume únicamente a través del BFF (`ms-cleanfresh-bff`, rutas `/api/reportes/**`, solo rol Admin). No valida JWT: confía en que solo el BFF le
habla. Proyecto individual de **DSY1107 Cloud Native 1** (DuocUC), Spec 044 del repo del frontend (EP5).

## Cómo funciona

```
orders ──► orders.events (Kafka, 3 particiones) ──► OrderEventListener ──► EventoOrdenService ──► orden_resumen (PostgreSQL) ──► ReporteService ──► API
                                                          │ falla                                   evento_procesado (idempotencia)
                                                          └──► orders.events.reportes.DLT
```

- **Consumidor** (`messaging/OrderEventListener`): `@KafkaListener` del grupo `reportes-kpis`, con commit manual (el offset avanza solo después de guardar el evento) y
  3 hilos, uno por partición. Solo entrega el mensaje y confirma: la lógica está en `EventoOrdenService`.
- **Lógica** (`service/EventoOrdenService`): valida el evento, descarta los repetidos por `eventId` (Kafka entrega "al menos una vez") y deja al día el resumen de la
  orden; un evento viejo no pisa a uno más nuevo.
- **Errores** (`config/KafkaConsumerConfig`): lo que no se puede leer o incumple el contrato (JSON roto, tipo desconocido, campos faltantes) va directo a
  `orders.events.reportes.DLT`; lo pasajero (la base cae un momento) se reintenta 3 veces con espera creciente (500 ms, 1 s, 2 s) y, si sigue fallando, va al
  mismo DLT. El DLT guarda el mensaje original y las cabeceras `kafka_dlt-*` con la excepción, su stack, el tópico, la partición y el offset de origen. Cada
  fallo queda en el log con ese contexto, y el consumo de los demás mensajes sigue.
- **Consumer group:** `reportes-kpis` es distinto del de `auditoria`, así que cada uno recibe todos los eventos. Si se levantan varias instancias de este servicio,
  Kafka reparte las particiones entre ellas (rebalanceo cooperativo).
- Un grupo nuevo empieza desde el principio (`auto.offset.reset=earliest`): los reportes se reconstruyen con la historia de eventos que Kafka retiene (7 días).

## Endpoints

Todos de solo lectura. `range` es `last1h`, `last24h`, `last7d` o `last30d`. Con Swagger: `http://localhost:8084/swagger-ui.html`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/reportes?range=` | Pedidos (no cancelados) por sucursal, `[{"branch":"Providencia","orders":42}]`; por defecto `last30d` |
| GET | `/api/reportes/kpis?range=` | Pedidos, ventas, ticket promedio, entregados, cancelados, **lead time** (promedio, mínimo y máximo en minutos) y **estados activos**; por defecto `last24h` |
| GET | `/api/reportes/ventas-por-hora?range=` | Pedidos y monto por cada hora del rango (con ceros); por defecto `last24h` |
| GET | `/api/reportes/top-servicios?range=&limite=` | Servicios más pedidos; por defecto `last7d` y 5 |

Definiciones: **ventas** = suma del total de los pedidos no cancelados creados en el rango; **lead time** = tiempo entre crear y entregar un pedido; **estados activos** =
órdenes que hoy siguen en CREADO, ACEPTADO, EN_PREPARACION o DESPACHADO. Un `range` desconocido responde 400.

## Configuración (variables de entorno)

La conexión a la base llega solo por variables de entorno, sin valores por defecto: si faltan, el servicio no arranca.

| Variable | Ejemplo / por defecto |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/reportes_db` |
| `DB_USER` / `DB_PASSWORD` | el usuario del servicio |
| `KAFKA_ENABLED` | `false` por defecto; `true` enciende el consumidor |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092,localhost:9093,localhost:9094` |

Las tablas (`orden_resumen`, `evento_procesado`) se crean solas (`ddl-auto: update`). La configuración completa del cliente Kafka (grupo, commit manual,
deserializadores, `CooperativeStickyAssignor`, timeouts) está en `spring.kafka.*` de `application.yaml`.

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21)
- PostgreSQL y, para consumir, el clúster Kafka de `EP5/despliegue/kafka/` del repo del frontend

## Levantar en local

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd clean package -DskipTests
$env:DB_URL="jdbc:postgresql://localhost:5432/reportes_db"; $env:DB_USER="reportes_user"; $env:DB_PASSWORD="<clave>"
$env:KAFKA_ENABLED="true"
java -jar target/ms-cleanfresh-reportes-0.0.1-SNAPSHOT.jar
```

## Probar

```powershell
.\mvnw.cmd test        # 30 pruebas, incluida una con un Kafka embebido: eventos buenos, repetidos, JSON roto y contrato incumplido
```

La prueba con Kafka embebido comprueba que el consumo no se detiene con mensajes malos y que estos llegan a `orders.events.reportes.DLT` con su error.

## Arquitectura y decisiones técnicas

Este servicio es parte de un sistema de varios repositorios. La documentación está en el repo del frontend:
[`CLAUDE.md`](https://github.com/frani3/cleanfresh-frontend/blob/main/CLAUDE.md) (contexto, AWS, roles y decisiones), [`EP5/README.md`](https://github.com/frani3/cleanfresh-frontend/blob/main/EP5/README.md) (Kafka) y la
[Spec 044](https://github.com/frani3/cleanfresh-frontend/blob/main/EP5/specs/044-spec-kafka-eventos-y-analitica.md). El [`CLAUDE.md`](CLAUDE.md) de este repo resume lo propio del servicio.

## Docker

```bash
docker build -t cleanfresh/reportes .
docker run -p 8084:8084 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... -e KAFKA_ENABLED=true -e KAFKA_BOOTSTRAP_SERVERS=<ip>:9092,<ip>:9093,<ip>:9094 cleanfresh/reportes
```
