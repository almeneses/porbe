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
- Validación de encabezados, fechas en formato `dd-mm-aaaa`, `dd/mm/aaaa`, `aaaa-mm-dd` o como fecha nativa de Excel, tipos de operación, ticker Yahoo, valores numéricos y coherencia del total.
- Prevención de importaciones duplicadas mediante la huella SHA-256 del archivo.
- Consulta de las últimas 200 operaciones en tabla para computador y tarjetas para móvil.
- Mensajes y errores de validación completamente en español.

El archivo contiene las hojas `Instrucciones`, `Operaciones` y `Ejemplos`. La hoja importable usa estas columnas:

| Columna | Regla principal |
| --- | --- |
| `fecha` | Fecha de la operación en `dd-mm-aaaa`, `dd/mm/aaaa` o `aaaa-mm-dd`; también admite una fecha nativa de Excel y no puede estar en el futuro. |
| `operación` | `compra`, `venta`, `dividendo`, `depósito`, `retiro`, `compra USD` o `venta USD`. |
| `ticker` | Símbolo de Yahoo Finance, por ejemplo `ECOPETROL.CL`. |
| `nombre` | Nombre del activo. |
| `cantidad` | Obligatoria para compras y ventas. |
| `precio unitario` | Obligatorio para compras y ventas. |
| `comisión` | Valor positivo o cero. |
| `total del movimiento` | Magnitud positiva del movimiento. |
| `notas` | Texto opcional. |
| `moneda` | COP o USD para acciones. Opcional en archivos antiguos, que mantienen COP. Cambios de moneda, depósitos y retiros usan COP. |

El signo en caja se deriva del tipo de operación: compras y retiros restan; ventas, dividendos y depósitos suman. Para compras y ventas se comprueba que el total coincida con cantidad por precio, ajustado por comisión.

### Dólares e inversiones internacionales

En **Operaciones**, `Compra de dólares` y `Venta de dólares` registran en una sola fila la cantidad USD, la tasa real COP/USD y la comisión COP. El total COP incluye la comisión al comprar y la descuenta al vender. No son aportes ni retiros del portafolio. Compras de acciones en USD consumen ese saldo; ventas y dividendos en USD lo aumentan. La interfaz y Excel permiten elegir explícitamente la moneda, incluso antes de sincronizar Yahoo.

`COP=X` se añade automáticamente a Mercado cuando hay movimientos USD, aunque sólo se hayan comprado dólares. Yahoo guarda su histórico diario como los demás precios, sin depender de una consulta en vivo para cada valoración. Su cotización no sustituye la tasa real de tus cambios de moneda. El acceso público a Yahoo para COP=X fue comprobado el 9 de octubre de 2026, sin garantía de disponibilidad futura.

Resumen, historial semanal y reportes expresan los importes de posiciones internacionales en COP. La fila `COP=X · Dólar disponible` muestra cantidad USD, costo promedio de adquisición COP y su valor actual COP. **Efectivo COP** sólo contiene pesos, por lo que los dólares no se suman dos veces. Las acciones USD mantienen cantidades de acciones y muestran precios equivalentes COP.

Se usa la última cotización USD/COP cuya fecha sea menor o igual al corte, nunca una futura. Costos, ventas y dividendos se convierten con la cotización histórica de su operación. Al consumir dólares se libera su costo promedio COP y se reconoce el efecto cambiario; al vender acciones o recibir dividendos se incorpora efectivo USD con costo COP de esa fecha. Las comisiones quedan incluidas. La ganancia económica total incluye bolsa y cambio de moneda, sin separar ambos efectos en paneles nuevos. La rentabilidad simple divide la ganancia por compras acumuladas de acciones COP más el total COP entregado al comprar dólares, evitando contar primero dólares y luego acciones USD como dos inversiones. Vender dólares o retirar efectivo no borra ese capital histórico; TWR/MWR conservan sólo depósitos/retiros COP como flujos externos.

Si faltan cotizaciones actuales o históricas, hay saldo USD negativo, ventas sin posición o un mismo ticker mezcla monedas, la valoración queda incompleta y las tasas TWR/MWR afectadas no se publican. Los totales parciales no representan la valoración completa. Las operaciones antiguas no se reescriben: conservan COP. Si Yahoo identifica una moneda incompatible, la posición se mantiene fuera del consolidado con aviso hasta corregir la moneda y registrar el efectivo USD previo. No se incluyen otras monedas, cuentas múltiples, depósitos/retiros USD ni cálculos fiscales.

### Incremento 3: datos de mercado

- Contrato `MarketDataProvider` con Yahoo Finance para los tickers habituales y Stock Analysis para `NUCO.CL` del MGC en COP.
- Yahoo se consulta mediante JSON. Para NUCO se extrae la serie estructurada incluida en el HTML público de `https://stockanalysis.com/quote/bvc/NUCO/history/`, sin ejecutar JavaScript ni abrir un navegador.
- Persistencia de apertura, máximo, mínimo, cierre, cierre ajustado y volumen diario.
- Actualización idempotente: ticker y fecha identifican un único precio, que se actualiza al volver a sincronizar.
- Diferenciación entre precio provisional de la sesión actual y cierre confirmado.
- Pantalla **Mercado** para consultar cobertura, último precio, días almacenados y ejecutar la actualización.
- Sincronización independiente por ticker: un símbolo con error no impide actualizar los demás.

La sincronización toma los tickers de las operaciones y comienza en el último día guardado con el mismo proveedor, incluyéndolo para corregir precios provisionales. Sin datos, o al cambiar de proveedor, comienza el 19 de enero de 2024. Para NUCO, la primera sincronización con Stock Analysis reemplaza únicamente las fechas disponibles en su serie pública y conserva las anteriores.

Stock Analysis expone actualmente unos seis meses de histórico en esa página. La sincronización avisa cuando no cubre todo el rango solicitado. No reconstruye automáticamente el histórico anterior a esa ventana. El precio del día se considera provisional hasta el día siguiente en Bogotá. Si la página falla, cambia de formato o no identifica NUCO en COP, se reporta el error sin recurrir a Yahoo ni modificar los precios guardados de NUCO. `NU` de Estados Unidos sigue usando Yahoo y se convierte a COP si sus operaciones se registran en USD.

Ninguna de estas consultas requiere credenciales. El scraping depende del formato y de la disponibilidad de la página pública, y no constituye una API con garantía de servicio.

Endpoints principales:

- `GET /api/market-data`: estado de precios para los tickers del portafolio.
- `POST /api/market-data/sync`: consulta el proveedor de cada ticker y actualiza los cierres diarios.
- `GET /api/market-data/{ticker}/daily?from=AAAA-MM-DD&to=AAAA-MM-DD`: serie diaria guardada.

### Corrección puntual de NUCO.CL

`scripts/replace_nuco_prices.py` usa la biblioteca estándar de Python y el PostgreSQL del servicio `database` de Docker Compose. Consulta Stock Analysis desde el **24 de abril de 2026 hasta el día actual en Bogotá**. Solo reemplaza `open_price`, `high_price`, `low_price`, `close_price` y `adjusted_close` de las fechas ya existentes de `NUCO.CL` en COP.

Vista previa, sin conexión a la base. El SQL generado termina en `ROLLBACK`:

```bash
python scripts/replace_nuco_prices.py > /tmp/nuco-prices-preview.sql
```

Para aplicar en el Compose local:

```bash
python scripts/replace_nuco_prices.py --apply
```

Para usar el Compose de producción, ejecute en el servidor correspondiente:

```bash
python scripts/replace_nuco_prices.py --compose-file compose.prod.yaml --apply
```

Para cargar un archivo `.env` específico, agregue `--env-file`:

```bash
python scripts/replace_nuco_prices.py --compose-file compose.prod.yaml --env-file .env.prod --apply
```

La ruta del `.env` se resuelve desde el directorio donde ejecuta el script y se entrega a Docker Compose. El script verifica que el archivo exista antes de descargar los precios. Si omite la opción, conserva el comportamiento habitual de Compose. El servicio `database` debe estar en ejecución; la conexión usa el usuario y la base configurados en ese contenedor.

Cada ejecución vuelve a consultar la fuente. Antes de actualizar, guarda las filas originales completas en una tabla `public.nuco_prices_backup_<fecha_UTC>`, cuyo nombre imprime el script. Respaldo y actualización se confirman en una sola transacción. Si faltan fechas en Porbe, la moneda no es COP o la fuente ya no cubre el inicio solicitado, aborta sin aplicar una corrección parcial. No inserta filas ni cambia volumen, fuente, estado de cierre o fechas de actualización.

Mantenga desplegada la selección de Stock Analysis para NUCO antes de reactivar la sincronización automática, para evitar que Yahoo sobrescriba la corrección. El precio del día puede ser provisional y las fechas sin datos en la fuente no se rellenan.

Para restaurar los cinco precios, sustituya `NOMBRE_DEL_RESPALDO` por la tabla que imprimió el script:

```sql
BEGIN;
UPDATE public.market_price_daily p
SET open_price = b.open_price, high_price = b.high_price, low_price = b.low_price,
    close_price = b.close_price, adjusted_close = b.adjusted_close
FROM public.NOMBRE_DEL_RESPALDO b
WHERE p.id = b.id AND p.instrument_id = b.instrument_id AND p.price_date = b.price_date;
COMMIT;
```

Pruebas del script:

```bash
python -m unittest discover -s scripts -p 'test_*.py'
```

### Incremento 4: posiciones y valoración actual

- Reconstrucción cronológica de la cantidad disponible por ticker.
- Costo promedio ponderado, costo vigente y capital total destinado a compras.
- Ganancia realizada en ventas y ganancia no realizada contra el último precio.
- Dividendos, efectivo acumulado, aportes netos y resultado total del portafolio.
- Detección de ventas superiores a la cantidad disponible.
- Valoración parcial explícita cuando falta un precio, una cotización histórica USD/COP o la moneda registrada es incompatible.
- Dashboard conectado a datos reales, con mejor y menor resultado por activo.
- Posiciones en tabla para computador y tarjetas para móvil.

La valoración usa los importes de compra con comisión incluida y los importes netos de venta. Las posiciones registradas en USD se convierten a COP con cotizaciones históricas de COP=X. Las monedas incompatibles se muestran individualmente y quedan fuera del total hasta corregir las operaciones.

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
- **Exportar portafolio** descarga todos los movimientos del portafolio seleccionado en `.xlsx`, aunque haya filtros activos. El archivo es compatible con la plantilla de importación de Porbe y conserva fechas, campos vacíos y precisión decimal.
- **Exportar filtrados** descarga únicamente los movimientos que cumplen los filtros activos.
- La reimportación mantiene los límites actuales de 5.000 movimientos y 5 MB por archivo. Si el portafolio los supera, utiliza filtros para exportarlo por partes.
- Recalculo inmediato del dashboard y el histórico después de cada cambio.
- Encabezados fijos y desplazamiento interno en las tablas que superan aproximadamente 20 filas.

Endpoints principales:

- `GET /api/operations`: consulta filtrada del libro.
- `POST /api/operations`: crea una operación manual.
- `PUT /api/operations/{id}`: modifica una operación conservando su origen.
- `DELETE /api/operations/{id}`: elimina una operación.
- `GET /api/operations/export?portfolioId=ID`: exporta todos los movimientos del portafolio a Excel. Los filtros opcionales permiten exportar un subconjunto.
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
- PDF A4 de dos páginas generado desde el mismo HTML de la imagen.
- Resultado en pesos y porcentaje del periodo, descontando depósitos y retiros.
- Acciones que más aumentaron o redujeron el resultado durante las fechas elegidas.
- Dividendos, ganancia, rentabilidad, aportes, efectivo y valor del portafolio.
- Gráfico histórico completo con valores de referencia fáciles de leer.
- Distribución del dinero por acción y por tipo de empresa.
- Historial persistente de informes con descargas posteriores.
- Actualización de precios y generación automática en el horario y para los portafolios elegidos desde **Configuración**.
- Interfaz de entrega desacoplada, preparada para conectar distintos proveedores de mensajería.
- Comentario opcional generado con IA, con un resumen sencillo y hasta dos posibilidades de acción.

La generación se realiza completamente en el backend a partir de
`backend/src/main/resources/templates/reports/portfolio-report.html`. Thymeleaf resuelve los datos y
Chromium, controlado por Playwright, genera el PNG y el PDF desde el mismo HTML. La tarea automática
abre su propio navegador sin interfaz; no depende de que el navegador del usuario permanezca abierto.

Docker ya incluye una versión compatible de Chromium. En desarrollo local se detecta Chromium en las
rutas comunes o puede indicarse explícitamente con `PORTFOLIO_REPORT_BROWSER_EXECUTABLE`.
En **Configuración → Indicaciones para el próximo informe**, seleccione el portafolio y guarde
una orientación de título, tema o tono. Cada guardado explícito reemplaza la anterior y activa
una nueva revisión, incluso si el texto es el mismo. Se utiliza una sola vez, únicamente cuando
el informe se guarda correctamente con comentario de IA. Si falla la IA o la generación, queda
pendiente. Descargar o enviar el informe no la consume. Cancelar no modifica un informe ya
iniciado; guardar otra durante la generación la conserva para el siguiente informe.

La IA recibe las posiciones del cierre consultado, con su costo promedio contable, moneda,
ganancia acumulada y fecha de precio. Ese promedio no representa el precio de una compra
concreta. También recibe la última compra registrada por activo hasta ese cierre, con su precio
unitario, comisión y total en la moneda original. Sin una indicación nueva, mantiene su comentario
habitual. Una generación reserva
la indicación hasta completar el informe; si el proceso se interrumpe, otro intento puede
recuperarla después de diez minutos. El informe antiguo no puede completar esa misma reserva.

Para incluir el comentario al ejecutar el backend directamente, inicie sesión una vez con `codex login`
y exporte `CODEX_COMMAND=codex` antes de iniciar Spring. Fuera de Docker, Codex se ejecuta en un
directorio temporal de solo lectura. Sin el comando, o si Codex falla, el informe omite la sección.
Si el IDE no encuentra `codex`, use la ruta absoluta mostrada por `command -v codex`.

En Docker, Codex ya viene instalado en la imagen del backend. Después de construirla, autentique el
contenedor una sola vez:

```bash
docker compose up -d --build backend
docker compose exec backend codex login --device-auth
docker compose exec backend codex login status
```

La sesión queda persistida en el volumen `porbe-codex-home`. Como Docker bloquea el sandbox Linux
anidado, el propio contenedor actúa como límite y Codex usa `danger-full-access` dentro de él. Trate el
volumen como una contraseña y use esta integración solo en un despliegue personal o de confianza.
Codex pertenece al backend; el contenedor de WhatsApp no lo necesita. En producción use los mismos
comandos agregando `-f compose.prod.yaml`.

Endpoints principales:

- `GET /api/reports`: historial de informes sin cargar los archivos binarios.
- `POST /api/reports`: genera y persiste PNG y PDF para el periodo solicitado.
- `GET /api/reports/{id}/image`: muestra o descarga la imagen.
- `GET /api/reports/{id}/pdf`: descarga el PDF.
- `GET /api/reports/schedule`: informa la próxima ejecución y el estado del canal de entrega.
- `PUT /api/portfolios/{id}/scheduled-report?enabled=true|false`: incluye o excluye un portafolio del informe automático.

### Incremento 9: envío temporal por WhatsApp Web

- Servicio Node independiente basado en `whatsapp-web.js` 1.34.7 y Chromium.
- Sesión `LocalAuth` persistida en el volumen Docker `porbe-whatsapp-data`.
- Código QR visible solamente en la pantalla autenticada de **Informes**.
- Comunicación Java–Node por HTTP dentro de la red privada de Docker; el servicio Node no publica puertos al host.
- Token compartido `X-Porbe-Internal-Token` para autenticar las solicitudes internas.
- Envío del PNG del informe a destinatarios persistidos y elegidos desde la UI.
- Confirmación antes de cada envío manual y estado de entrega conservado en el historial.
- Entrega automática a todos los destinatarios activos.

Para vincular una cuenta por primera vez:

1. Inicie la aplicación con `docker compose up --build`.
2. Abra **Informes** y espere a que aparezca el código QR.
3. En el celular abra WhatsApp → **Dispositivos vinculados** → **Vincular un dispositivo**.
4. Escanee el QR. Al aparecer **WhatsApp conectado**, abra **Configuración → WhatsApp** y agregue los destinatarios con código de país.

Los informes semanales se envían a todos los destinatarios activos configurados en la aplicación.
La pantalla **Configuración** también resume la próxima ejecución, el último resultado y el estado de WhatsApp.
En un despliegue que no sea exclusivamente local debe reemplazar `WHATSAPP_INTERNAL_TOKEN`
por un secreto largo y aleatorio.

Endpoints añadidos:

- `GET /api/reports/whatsapp/status`: estado, cuenta enmascarada y QR vigente.
- `GET|POST /api/reports/whatsapp/recipients`: consulta o crea destinatarios.
- `PUT|DELETE /api/reports/whatsapp/recipients/{id}`: modifica o elimina un destinatario.
- `POST /api/reports/whatsapp/recipients/{id}/test`: envía el último informe como prueba.
- `POST /api/reports/{id}/whatsapp`: envía manualmente el PNG a un destinatario guardado.

`whatsapp-web.js` automatiza WhatsApp Web y no es una API oficial de Meta. Puede dejar de funcionar
si WhatsApp cambia su cliente web y existe riesgo de desconexión o bloqueo de la cuenta. Se recomienda
usar una cuenta separada para pruebas y migrar a la API oficial antes de un uso productivo. El árbol de
Puppeteer también mantiene una alerta de `npm audit` en su descargador de Chromium; Porbe desactiva esa
ruta, instala Chromium desde Debian y ejecuta el contenedor sin privilegios, pero la alerta transitiva
seguirá apareciendo hasta que el proyecto publique una dependencia corregida.

## Ejecución con Docker

1. Copie `.env.example` como `.env` si desea cambiar puertos o credenciales de infraestructura.
2. Ejecute `docker compose up --build`.
3. Abra `http://localhost:3000`.
4. Ingrese con `admin` / `admin` en el entorno local.

El backend queda disponible en `http://localhost:8080` y PostgreSQL en `localhost:5432`.
Si alguno de esos puertos ya está ocupado, cámbielo en `.env`; por ejemplo, use `PORBE_API_PORT=18080` para la API.

Yahoo se puede redirigir para pruebas mediante `YAHOO_FINANCE_BASE_URL`. Ambos clientes HTTP comparten el agente de usuario `YAHOO_FINANCE_USER_AGENT` y los tiempos máximos `YAHOO_FINANCE_CONNECT_TIMEOUT_SECONDS` y `YAHOO_FINANCE_READ_TIMEOUT_SECONDS`.

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

`admin/admin` y `porbe_whatsapp_local` son exclusivamente valores iniciales de desarrollo. Para cualquier
despliegue, configure `APP_SECURITY_ADMIN_USERNAME`, `APP_SECURITY_ADMIN_PASSWORD`,
`WHATSAPP_INTERNAL_TOKEN` y habilite cookies seguras detrás de HTTPS. El volumen
`porbe-whatsapp-data` contiene la sesión vinculada y debe tratarse como una credencial sensible.
