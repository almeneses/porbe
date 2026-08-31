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

### Incremento 5: histórico semanal

- Reconstrucción del portafolio para cada viernes ya finalizado desde la primera operación.
- Cantidad, precio de cierre, fecha efectiva del precio y valor de mercado por ticker y semana.
- Uso del último cierre disponible anterior al viernes cuando la jornada fue festiva.
- Capital invertido vigente, aportes netos, dividendos, efectivo, ganancias y rentabilidad acumulada.
- Variación nominal y porcentual contra el cierre semanal anterior.
- Gráfico interactivo del valor del portafolio frente al capital aportado, con rangos de 12, 26 y 52 semanas o todo el período.
- Tabla para computador y tarjetas adaptables para móvil, compatibles con los temas claro y oscuro.
- Señalización de semanas parciales por precios faltantes, monedas externas u operaciones inconsistentes.

Endpoint principal:

- `GET /api/portfolio/history/weekly?from=AAAA-MM-DD&to=AAAA-MM-DD`: histórico semanal; ambas fechas son opcionales.

### Incremento 6: administración de operaciones

- Creación, modificación y eliminación manual desde la pantalla **Operaciones**.
- Las operaciones manuales usan las mismas reglas financieras y de formato que la importación Excel.
- Filtros por rango de fechas, ticker, tipo, origen y archivo importado.
- Identificación de la operación exacta que deja negativa la cantidad de un ticker.
- Origen visible para cada registro: archivo Excel o captura manual.
- Reversión atómica de una importación completa con confirmación previa.
- Bitácora de creaciones, modificaciones, eliminaciones y reversiones con usuario y fecha.
- Exportación `.xlsx` del libro corregido, compatible con el formato de importación.
- Recalculo inmediato del dashboard y el histórico después de cada cambio.
- Encabezados fijos y desplazamiento interno en las tablas que superan aproximadamente 20 filas.

Endpoints principales:

- `GET /api/operations`: consulta filtrada del libro.
- `POST /api/operations`: crea una operación manual.
- `PUT /api/operations/{id}`: modifica una operación conservando su origen.
- `DELETE /api/operations/{id}`: elimina una operación.
- `GET /api/operations/export`: exporta el resultado filtrado a Excel.
- `GET /api/operation-batches`: lista importaciones de Excel.
- `DELETE /api/operation-batches/{id}`: revierte una importación completa.
- `GET /api/operation-audit`: consulta las últimas acciones administrativas.

### Incremento 7: rentabilidad, distribución y actualización automática

- Rentabilidad TWR semanal que descuenta depósitos y retiros externos del rendimiento.
- TWR acumulada y anualizada para todo el período disponible.
- Rentabilidad contable por activo, con resultado realizado, no realizado y dividendos.
- Participación de cada acción sobre el valor de mercado del portafolio.
- Distribución por sector; la clasificación sugerida puede corregirse desde **Mercado**.
- Gráficos adaptables de composición por acción, composición sectorial, ganancias y dividendos.
- Histórico del último cierre disponible para cada viernes desde el 19 de enero de 2024, para todos los tickers actuales.
- Programación semanal persistente por día y hora en la zona `America/Bogota`.
- Ejecución automática en el backend, incluso sin una sesión web abierta, con estado de la última y próxima ejecución.

El TWR se calcula encadenando los rendimientos semanales. Para cada semana se resta del valor final el flujo externo neto —depósitos menos retiros— y se compara con el valor del viernes anterior. La anualización se muestra cuando existe más de un cierre semanal.

Endpoints principales:

- `GET /api/market-data/weekly-closes`: cierres de todos los viernes desde `2024-01-19`.
- `GET /api/market-data/schedule`: consulta la programación automática.
- `PUT /api/market-data/schedule`: activa o modifica día y hora.
- `PUT /api/market-data/{ticker}/sector`: corrige la clasificación sectorial de un activo.
- `GET /api/portfolio/history/weekly`: incluye flujo externo, rendimiento semanal, TWR y TWR anualizada.

### Incremento 8: informes de rendimiento

- Informe de rendimiento para un rango de fechas elegido por el usuario.
- Imagen vertical PNG consistente con la identidad visual de Porbe.
- PDF de una página generado desde la misma composición gráfica.
- Resultado nominal y TWR del periodo, descontando depósitos y retiros.
- Mayor y menor valorización del periodo y rentabilidad acumulada por activo.
- Dividendos, ganancia, rentabilidad, aportes, efectivo y valor del portafolio.
- Gráfico de valor del portafolio frente a los aportes y movimientos recientes.
- Historial persistente de informes con descargas posteriores.
- Actualización de precios y generación automática cada viernes a las 17:30 en `America/Bogota`.
- Interfaz de entrega desacoplada; WhatsApp Business permanece sin configurar y no envía mensajes.
- La sección de notas se mantiene oculta hasta incorporar un resumen asistido por IA.

La generación se realiza completamente en el backend. Java2D construye la imagen y Apache PDFBox 3.0.8 crea el PDF, por lo que la tarea automática no necesita que el navegador permanezca abierto.

Endpoints principales:

- `GET /api/reports`: historial de informes sin cargar los archivos binarios.
- `POST /api/reports`: genera y persiste PNG y PDF para el periodo solicitado.
- `GET /api/reports/{id}/image`: muestra o descarga la imagen.
- `GET /api/reports/{id}/pdf`: descarga el PDF.
- `GET /api/reports/schedule`: informa la próxima ejecución y el estado del canal de entrega.

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
