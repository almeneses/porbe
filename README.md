# Porbe

Seguimiento y análisis de portafolios de inversión, inicialmente enfocado en acciones de la Bolsa de Valores de Colombia.

## Estado actual

### Incremento 1: base de la aplicación

- API con Spring Boot 4 y Java 21.
- Interfaz React en español, preparada para i18n.
- Diseño adaptable para computador y móvil.
- Temas claro, oscuro y según el sistema.
- Inicio de sesión por cookie de sesión con protección CSRF.
- PostgreSQL y migraciones Flyway.
- Contenedores para base de datos, backend y frontend.

### Incremento 2: operaciones e importación Excel

- Plantilla oficial descargable desde la pantalla **Importar portafolio**.
- Importación `.xlsx` transaccional: si alguna fila es inválida no se guarda ninguna operación.
- Validación de encabezados, fechas en formato `dd/mm/aaaa`, `aaaa-mm-dd` o como fecha nativa de Excel, tipos de operación, ticker Yahoo, valores numéricos y coherencia del total.
- Prevención de importaciones duplicadas mediante la huella SHA-256 del archivo.
- Consulta de las últimas 200 operaciones en tabla para computador y tarjetas para móvil.
- Mensajes y errores de validación completamente en español.

El archivo contiene las hojas `Instrucciones`, `Operaciones` y `Ejemplos`. La hoja importable usa estas columnas:

| Columna | Regla principal |
| --- | --- |
| `fecha` | Fecha de la operación en `dd/mm/aaaa` o `aaaa-mm-dd`; también admite una fecha nativa de Excel y no puede estar en el futuro. |
| `operación` | `compra`, `venta`, `dividendo`, `depósito` o `retiro`. |
| `ticker` | Símbolo de Yahoo Finance, por ejemplo `ECOPETROL.CL`. |
| `nombre` | Nombre del activo. |
| `cantidad` | Obligatoria para compras y ventas. |
| `precio unitario` | Obligatorio para compras y ventas. |
| `comisión` | Valor positivo o cero. |
| `total del movimiento` | Magnitud positiva del movimiento. |
| `notas` | Texto opcional. |

El signo en caja se deriva del tipo de operación: compras y retiros restan; ventas, dividendos y depósitos suman. Para compras y ventas se comprueba que el total coincida con cantidad por precio, ajustado por comisión.

### Incremento 3: datos de mercado

- Proveedor `MarketDataProvider` desacoplado, con implementación inicial para Yahoo Finance.
- Consulta JSON de velas diarias; no se extrae HTML de las páginas de Yahoo.
- Persistencia de apertura, máximo, mínimo, cierre, cierre ajustado y volumen diario.
- Actualización idempotente: ticker y fecha identifican un único precio, que se actualiza al volver a sincronizar.
- Diferenciación entre precio provisional de la sesión actual y cierre confirmado.
- Pantalla **Mercado** para consultar cobertura, último precio, días almacenados y ejecutar la actualización.
- Sincronización independiente por ticker: un símbolo con error no impide actualizar los demás.

La sincronización toma automáticamente los tickers presentes en las operaciones y consulta datos desde siete días antes de la primera operación hasta la fecha actual. Yahoo Finance no requiere credenciales en esta integración, pero su endpoint público no ofrece un contrato de servicio formal; el adaptador permite sustituirlo más adelante.

Endpoints principales:

- `GET /api/market-data`: estado de precios para los tickers del portafolio.
- `POST /api/market-data/sync`: consulta Yahoo y actualiza los cierres diarios.
- `GET /api/market-data/{ticker}/daily?from=AAAA-MM-DD&to=AAAA-MM-DD`: serie diaria guardada.

### Incremento 4: posiciones y valoración actual

- Reconstrucción cronológica de la cantidad disponible por ticker.
- Costo promedio ponderado, costo vigente y capital total destinado a compras.
- Ganancia realizada en ventas y ganancia no realizada contra el último precio.
- Dividendos, efectivo acumulado, aportes netos y resultado total del portafolio.
- Detección de ventas superiores a la cantidad disponible.
- Valoración parcial explícita cuando falta un precio o el activo usa otra moneda.
- Dashboard conectado a datos reales, con mejor y menor resultado por activo.
- Posiciones en tabla para computador y tarjetas para móvil.

La valoración usa los importes de compra con comisión incluida y los importes netos de venta. Las posiciones en una moneda diferente a la moneda base se muestran individualmente, pero se excluyen del total hasta incorporar conversión de divisas.

Endpoint principal:

- `GET /api/portfolio/summary`: resumen de efectivo, valoración, rendimiento y posiciones actuales.

## Ejecución con Docker

1. Copie `.env.example` como `.env` si desea cambiar puertos o credenciales.
2. Ejecute `docker compose up --build`.
3. Abra `http://localhost:3000`.
4. Ingrese con `admin` / `admin` en el entorno local.

El backend queda disponible en `http://localhost:8080` y PostgreSQL en `localhost:5432`.
Si alguno de esos puertos ya está ocupado, cámbielo en `.env`; por ejemplo, use `PORBE_API_PORT=18080` para la API.

El proveedor se puede redirigir para pruebas o reemplazo mediante `YAHOO_FINANCE_BASE_URL`. Los tiempos máximos de conexión y lectura se configuran con `YAHOO_FINANCE_CONNECT_TIMEOUT_SECONDS` y `YAHOO_FINANCE_READ_TIMEOUT_SECONDS`.

## Verificación

Backend:

```bash
docker run --rm -v "$PWD/backend:/workspace" -w /workspace maven:3.9.11-eclipse-temurin-25 mvn --batch-mode test
```

Frontend:

```bash
cd frontend
npm test -- --run
npm run lint
npm run build
```

## Seguridad

`admin/admin` es exclusivamente el valor inicial de desarrollo. Para cualquier despliegue, configure `APP_SECURITY_ADMIN_USERNAME`, `APP_SECURITY_ADMIN_PASSWORD` y habilite cookies seguras detrás de HTTPS.
