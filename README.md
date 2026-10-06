# ms-cleanfresh-reportes

Microservicio de reportes de Clean&Fresh Manager. Spring Boot 4.1.1 / Java
21, puerto **8084**. En la entrega 2 es un **esqueleto**: devuelve una
respuesta fija, sin base de datos y sin llamar a otros servicios. El cálculo
real (órdenes e ingresos por sucursal) se implementa en la siguiente entrega.

Se consume únicamente a través del BFF (`ms-cleanfresh-bff`, ruta
`/api/reportes`, solo rol Admin). No valida JWT: confía en que solo el BFF le
habla.

Proyecto individual de **DSY1107 Cloud Native 1** (DuocUC).

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/reportes` | Órdenes por sucursal (datos fijos de ejemplo) |

Respuesta (misma forma que usa hoy la pestaña "Analítica por sucursal" del
panel Admin):

```json
[{"branch":"Providencia","orders":42},{"branch":"Ñuñoa","orders":35},
 {"branch":"Las Condes","orders":28},{"branch":"Maipú","orders":19}]
```

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21)

## Levantar en local

```powershell
.\mvnw.cmd spring-boot:run
```

O compilar y correr el jar:

```powershell
.\mvnw.cmd clean package
java -jar target\ms-cleanfresh-reportes-0.0.1-SNAPSHOT.jar
```

Corre en `http://localhost:8084`. Probar: `curl http://localhost:8084/api/reportes`.

## Arquitectura y decisiones técnicas

Ver [`CLAUDE.md`](CLAUDE.md) y, en el repo del frontend,
`EP2/ARQUITECTURA.md`.
