# Manual de usuario · Consola DBA para SQL Server

**Curso:** EIF402 – Administración de Bases de Datos · Universidad Nacional
**Grupo 2:** Kenneth Ramírez Ugalde, Laura Fonseca Brenes, Brenda Serrano Jiménez,
Ahymara Lucila Ureña Chanto, Mariana Villalobos Ramírez
**Versión del documento:** 1.0 · octubre de 2026

---

## 1. Propósito

La Consola DBA es una aplicación web que permite a un administrador de bases de datos
observar el estado de una instancia de Microsoft SQL Server sin escribir consultas. Toda la
información proviene del catálogo del sistema y de las vistas de administración dinámica
(DMV) del propio gestor; la consola la interpreta con umbrales y recomendaciones.

Cada panel indica en su esquina la **fuente** de donde obtiene los datos (por ejemplo,
`sys.dm_exec_requests`), de modo que cualquier valor puede verificarse directamente en
SQL Server.

---

## 2. Requisitos

| Componente | Versión |
|---|---|
| JDK | 17 o superior (el proyecto compila con `release 17`; probado con JDK 21) |
| Maven | 3.9 o superior |
| Node.js y npm | Node 18 o superior |
| SQL Server | 2017 o superior (probado con SQL Server 2022 Developer en Docker) |
| Navegador | Cualquiera reciente (Chrome, Edge, Firefox, Safari) |

---

## 3. Instalación

### 3.1 Base de datos de ejemplo (opcional)

Si no se cuenta con una base propia, la carpeta `db/` crea `QuinielasDB` con datos de ejemplo.
Ejecute los scripts en orden: `01_create_database.sql` a `05_sample_data.sql`.

### 3.2 Objetos de la herramienta

Sobre la base que se desea monitorear, ejecute en orden:

```
sql/01_esquema_dba.sql          Esquema dba: histórico, bitácora y objetos inválidos
sql/02_procedimientos.sql       Procedimientos dba.usp_* de mantenimiento y capturas
sql/04_permisos_monitoreo.sql   Login dba_monitor con privilegios mínimos
```

Antes de ejecutar el script 04, reemplace el marcador `N'CAMBIAR_ANTES_DE_EJECUTAR'` por
una contraseña propia y ajuste el nombre de la base. **No guarde esa contraseña en el
repositorio.**

`sql/05_datos_prueba.sql` y `sql/06_preparar_demostracion.sql` son opcionales: generan carga
de trabajo y condiciones para que cada módulo tenga algo que mostrar.

### 3.3 Backend

Las credenciales se pasan por variables de entorno, en la misma terminal donde se levanta el
servidor:

```bash
export SQLSERVER_HOST=localhost
export SQLSERVER_PUERTO=1433
export SQLSERVER_BD=QuinielasDB
export SQLSERVER_USUARIO=dba_monitor
export SQLSERVER_CONTRASENA='<su contraseña>'

cd backend
mvn spring-boot:run
```

El backend queda disponible en `http://localhost:8080`. Las variables solo existen mientras
esa terminal esté abierta.

### 3.4 Frontend

En una segunda terminal:

```bash
cd frontend
npm install        # solo la primera vez
npm run dev
```

Abra `http://localhost:5173` en el navegador.

---

## 4. La pantalla principal

La consola tiene tres zonas:

- **Barra superior (regleta de instrumentos).** Muestra la instancia conectada y la conexión
  activa (`servidor / base de datos`). Se actualiza cada 30 segundos. Si la instancia deja de
  responder, la barra lo señala.
- **Menú lateral.** Lista los seis módulos, M1 a M6. Un clic abre el módulo.
- **Área de contenido.** Muestra el módulo seleccionado. Todos comparten el mismo encabezado,
  con el título, una descripción, el **selector de conexión** y el botón para **recargar** los
  datos.

### 4.1 Cambiar de instancia

Si `application.yml` define varias conexiones en `dba.conexiones` (ver
`application-ejemplo.yml`), el selector de conexión del encabezado permite cambiar entre
ellas sin recompilar ni reiniciar. Al cambiar, la consola recarga la barra superior y el
módulo abierto.

### 4.2 Mensajes que pueden aparecer

| Mensaje | Significado |
|---|---|
| Recuadro de error con botón de reintento | La consulta falló (instancia detenida, permiso faltante, credenciales). Corrija la causa y pulse reintentar. |
| Advertencias en la parte superior del módulo | El backend detectó una condición que limita la información (por ejemplo, falta el Agente SQL Server). |
| Diagnóstico | Lectura administrativa de los datos: qué está bien y qué requiere atención. |
| Tabla con mensaje en lugar de filas | No hay datos que mostrar; el mensaje explica por qué y qué hacer. |

---

## 5. Uso de cada módulo

### M1 · Instancia

Muestra la identidad y la disponibilidad del servidor.

- **Indicadores:** servidor, instancia, versión, estado, hora de inicio, tiempo de actividad,
  CPU lógicas y memoria utilizada.
- **Identificación del gestor:** edición, intercalación y descripción completa de la versión.
- **Servicios de la instancia:** estado del motor y del Agente SQL Server. En algunas
  ediciones o sin el permiso `VIEW SERVER STATE` este panel aparece vacío y lo indica.
- **Bases de datos administradas:** cada base de la instancia con su estado y tamaño.
- **Distribución de memoria:** qué componentes del motor consumen más memoria.

*Cómo leerlo:* si este módulo carga, la instancia está en línea y atendiendo peticiones. El
tiempo de actividad indica desde cuándo se acumulan las estadísticas de M2.

### M2 · Rendimiento

Se refresca automáticamente cada 20 segundos.

- **Controles:** *Ordenar consultas por* (tiempo de CPU acumulado, duración acumulada,
  lecturas lógicas o cantidad de ejecuciones) y *Cantidad de filas* (10, 15, 25 o 50).
- **Indicadores:** consultas en caché, ejecuciones totales, duración promedio, sesiones
  activas y sesiones bloqueadas.
- **Consultas de mayor consumo** y su gráfico de consumo, con el texto de cada consulta.
- **Sesiones activas:** sesiones de usuario conectadas.
- **Sesiones bloqueadas:** qué sesión espera, cuál la bloquea, el tipo de espera y el tiempo.
- **Principales tipos de espera** de la instancia.

*Cómo leerlo:* una sesión bloqueada más de **5 segundos** se considera crítica y el
diagnóstico lo destaca. Las estadísticas de consultas son acumuladas desde el último
reinicio; si el caché está vacío, ejecute alguna carga de trabajo.

### M3 · Almacenamiento

- **Indicadores:** base de datos, tamaño total, espacio realmente usado, archivos de datos y
  registro de transacciones.
- **Espacio por filegroup** y gráfico de **ocupación por filegroup**.
- **Archivos de datos y registro:** tamaño, uso y crecimiento configurado de cada archivo.
- **Objetos con mayor consumo:** tablas e índices que más espacio ocupan.
- **Crecimiento de la base de datos:** evolución en el tiempo.
- **Botón «Registrar captura»:** guarda el tamaño actual en `dba.snapshot_almacenamiento`.

*Cómo leerlo:* un filegroup al **75 %** o más se marca como advertencia y al **90 %** o más
como crítico. Para trazar el crecimiento se necesitan al menos dos capturas; mientras no
existan, la consola usa como fuente alterna el tamaño de los respaldos completos e indica en
pantalla cuál fuente está mostrando.

### M4 · Respaldos

- **Estado por base de datos:** último respaldo completo, diferencial y de log, modelo de
  recuperación y nivel de riesgo.
- **Historial de respaldos:** fecha, tipo, duración, tamaño y archivo de destino.
- **Ejecuciones de jobs de respaldo:** historial del Agente SQL Server, incluidos los fallos.

*Cómo leerlo:*

| Nivel | Condición |
|---|---|
| ALTO | No existe respaldo completo, o el último tiene más de 72 horas |
| MEDIO | El último respaldo completo tiene más de 24 horas, o la base está en modelo FULL sin respaldos de log |
| BAJO | El respaldo completo está al día |

`msdb.dbo.backupset` solo registra respaldos exitosos; los fallidos se ven en el panel de
jobs. En SQL Server Express no existe el Agente, por lo que ese panel queda vacío con un
mensaje explicativo.

### M5 · Auditoría

- **Indicadores:** logins de instancia, logins deshabilitados, miembros de sysadmin, usuarios
  de la base, asignaciones de rol y objetos con problemas.
- **Logins registrados:** tipo, estado y si pertenecen a sysadmin.
- **Usuarios de la base de datos**, **Roles y miembros** y **Privilegios asignados**.
- **Objetos con dependencias rotas:** vistas, procedimientos o funciones que hacen
  referencia a objetos inexistentes.

*Cómo leerlo:* el diagnóstico señala como puntos de atención más de dos logins en
sysadmin, la cuenta `sa` habilitada y concesiones del privilegio `CONTROL`.

### M6 · Mantenimiento

- **Indicadores:** estadísticas revisadas, estadísticas que requieren recálculo, índices
  analizados y objetos inválidos.
- **Acciones de mantenimiento:**
  - *Esquema (opcional)* y *Tabla (vacío = toda la base de datos)* acotan el alcance.
  - *Muestreo:* Automático o Completo (FULLSCAN).
  - **«Recalcular estadísticas»** ejecuta `dba.usp_actualizar_estadisticas`.
  - **«Validar objetos»** intenta refrescar cada módulo con `sp_refreshsqlmodule` y registra
    en `dba.objeto_invalido` los que fallan.
  - **«Recompilar pendientes»** vuelve a intentar los objetos registrados como inválidos.
- **Estadísticas desactualizadas**, **Fragmentación de índices** (índices de más de 100
  páginas), **Objetos inválidos detectados** y **Bitácora de mantenimiento**.

*Cómo leerlo:* se recomienda recalcular una estadística con **500 o más modificaciones**
pendientes, y revisarla si lleva más de 30 días sin actualizarse. Para índices: **REBUILD**
desde 30 % de fragmentación, **REORGANIZE** entre 10 % y 30 %, sin acción por debajo de 10 %.

Los nombres de esquema y tabla se validan antes de enviarse; un nombre con caracteres no
permitidos se rechaza antes de llegar a la base. Toda acción que llega a ejecutarse queda
registrada en la bitácora de mantenimiento; las que el gestor rechaza (por ejemplo, un esquema
inexistente) muestran un mensaje de error pero no se registran.

**Ejemplo de uso de «Validar» y «Recompilar»:** si una vista depende de una tabla que fue
eliminada, M5 la muestra como dependencia rota; «Validar objetos» la registra como inválida;
al recrear la tabla, «Recompilar pendientes» la corrige y desaparece de la lista.

---

## 6. Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| Ningún módulo carga, error de conexión | Contenedor o servicio de SQL Server detenido | `docker start sqlserver2022` y esperar unos 30 s |
| Error de inicio de sesión | Variables de entorno vacías (terminal nueva) | Volver a ejecutar los `export` de la sección 3.3 |
| El build de Maven falla por la versión de Java | El editor cambió `<java.version>` en `pom.xml` | Devolverlo a `17` |
| Un panel indica falta de permiso | Al login le falta un `GRANT` | Revisar y volver a ejecutar `sql/04_permisos_monitoreo.sql` |
| M2 sin consultas | Instancia recién reiniciada, caché vacío | Ejecutar carga de trabajo (`sql/05_datos_prueba.sql`) |
| M4 sin jobs | Edición Express o Agente detenido | Comportamiento esperado; los respaldos manuales sí aparecen |

---

## 7. Limitaciones

- La consola no tiene autenticación propia; está pensada para una red interna.
- Las métricas de M2 se reinician con la instancia o con `DBCC FREEPROCCACHE`.
- La fragmentación se mide en modo `LIMITED` para no cargar la instancia monitoreada.
