# Consola DBA para Microsoft SQL Server

Herramienta web de monitoreo, administración y auditoría de un SGBD, desarrollada para el
curso **EIF402 – Administración de Bases de Datos** (Universidad Nacional).

La aplicación no reimplementa la lógica del gestor: **consulta el propio catálogo y las vistas
de administración dinámica (DMV) de SQL Server** y presenta esa información con una lectura
administrativa (umbrales, niveles de riesgo, recomendaciones).

---

## Arquitectura

```
Navegador                Servidor de aplicaciones            SGBD
┌──────────────┐  HTTP  ┌───────────────────────────┐  JDBC  ┌──────────────┐
│ React + Vite │ ─────▶ │ Spring Boot (patrón MVC)  │ ─────▶ │  SQL Server  │
│  (cliente)   │ ◀───── │ Controlador → Servicio →  │ ◀───── │  catálogo    │
└──────────────┘  JSON  │ Repositorio               │        │  + DMV       │
                        └───────────────────────────┘        └──────────────┘
```

| Capa | Responsabilidad | Ubicación |
|---|---|---|
| Controlador | Expone los endpoints REST, valida la entrada | `*Controlador.java` |
| Servicio | Interpreta las métricas, calcula umbrales y diagnósticos | `*Servicio.java` |
| Repositorio | Ejecuta las consultas T-SQL y mapea los resultados | `*Repositorio.java` |
| DTO | Contrato de datos hacia el cliente (`record` de Java) | `*Dtos.java` |
| Vista | Componentes React que renderizan cada módulo | `frontend/src/pages` |

Cada módulo del enunciado es un paquete Java independiente, para que el trabajo pueda
repartirse entre los integrantes sin conflictos de merge.

---

## Requisitos

- **JDK 25** o superior
- **Maven 3.9+**
- **Node.js 18+** y npm
- **SQL Server 2017 o superior** (Express sirve; ver limitaciones más abajo)
- Una base de datos con datos suficientes para que las métricas sean significativas

---

## Instalación

### 1. Preparar la base de datos

Ejecute los scripts en orden desde SSMS o Azure Data Studio, **sobre la base de datos que va
a monitorear**:

```
sql/01_esquema_dba.sql          Tablas de apoyo (histórico, bitácora, objetos inválidos)
sql/02_procedimientos.sql       Procedimientos de mantenimiento y snapshots
sql/04_permisos_monitoreo.sql   Login dedicado con privilegios mínimos
```

`sql/03_consultas_administrativas.sql` no se ejecuta durante la instalación: es el catálogo
documentado de todas las consultas que usa la herramienta, para poder validarlas por separado
y para la defensa del proyecto.

En el script 04, **sustituya la contraseña y el nombre de la base de datos** antes de
ejecutarlo. No la escriba en el repositorio.

### 2. Configurar y levantar el backend

Las credenciales se leen de variables de entorno, nunca del código fuente:

```bash
export SQLSERVER_HOST=localhost
export SQLSERVER_PUERTO=1433
export SQLSERVER_BD=NombreDeSuBaseDeDatos
export SQLSERVER_USUARIO=dba_monitor
export SQLSERVER_CONTRASENA='...'

cd backend
mvn spring-boot:run
```

El backend queda en `http://localhost:8080`.

Para monitorear varias instancias, agregue más entradas en `dba.conexiones` dentro de
`application.yml` (vea `application-ejemplo.yml`). La consola permite cambiar de instancia
desde el selector superior **sin recompilar**.

### 3. Levantar el frontend

```bash
cd frontend
npm install
npm run dev
```

Abra `http://localhost:5173`. Vite redirige `/api` al backend, así que no hay URLs
codificadas en el cliente.

---

## Los seis módulos

| # | Módulo | Qué muestra | Fuentes en SQL Server |
|---|---|---|---|
| M1 | Instancia | Servidor, versión, edición, estado, arranque, uptime, memoria, bases administradas | `SERVERPROPERTY`, `sys.dm_os_sys_info`, `sys.dm_server_services`, `sys.databases`, `sys.dm_os_memory_clerks` |
| M2 | Rendimiento | Consultas más costosas, tiempos de ejecución, sesiones activas, bloqueos, esperas | `sys.dm_exec_query_stats`, `sys.dm_exec_sql_text`, `sys.dm_exec_sessions`, `sys.dm_exec_requests`, `sys.dm_os_wait_stats` |
| M3 | Almacenamiento | Espacio por filegroup y archivo, objetos más grandes, crecimiento histórico | `sys.database_files`, `sys.filegroups`, `FILEPROPERTY`, `sys.dm_db_partition_stats`, `dba.snapshot_almacenamiento` |
| M4 | Respaldos | Último respaldo por tipo, historial, duración, tamaño, nivel de riesgo | `msdb.dbo.backupset`, `backupmediafamily`, `sysjobhistory` |
| M5 | Auditoría | Logins, usuarios, roles, privilegios, objetos con dependencias rotas | `sys.server_principals`, `sys.database_principals`, `sys.database_permissions`, `sys.sql_expression_dependencies` |
| M6 | Mantenimiento | Estadísticas desactualizadas, fragmentación, validación y recompilación de objetos | `sys.stats`, `sys.dm_db_stats_properties`, `sys.dm_db_index_physical_stats`, `sp_refreshsqlmodule` |

---

## Decisiones de diseño que conviene poder defender

**Por qué SQL Server no tiene "objetos inválidos" como Oracle.**
Oracle marca un objeto como `INVALID` en `USER_OBJECTS` cuando cambia una dependencia.
SQL Server usa *deferred name resolution*: un procedimiento cuya tabla base fue eliminada
sigue existiendo en `sys.objects` y solo falla al ejecutarse. Por eso la herramienta usa dos
mecanismos complementarios: `sys.sql_expression_dependencies` detecta referencias a entidades
inexistentes (módulo 5), y `dba.usp_validar_modulos` intenta refrescar cada módulo con
`sp_refreshsqlmodule` dentro de un `TRY...CATCH`, registrando los que fallan (módulo 6).

**Por qué hay una tabla propia para el crecimiento.**
SQL Server no conserva histórico de tamaño de archivos. El módulo 3 registra capturas en
`dba.snapshot_almacenamiento` (botón «Registrar captura» o un job diario). Mientras no haya
suficientes capturas, la herramienta muestra el crecimiento inferido del tamaño de los
respaldos completos, e indica en pantalla cuál de las dos fuentes está usando.

**Por qué `backupset` nunca muestra respaldos fallidos.**
`msdb.dbo.backupset` solo registra respaldos que terminaron con éxito. Los fallos se detectan
en `msdb.dbo.sysjobhistory`. La consola consulta ambas y lo advierte explícitamente.

**Por qué las acciones de mantenimiento pasan por procedimientos almacenados.**
El backend nunca concatena SQL dinámico. Las operaciones que modifican estado se invocan como
`EXEC dba.usp_...` con parámetros, y los identificadores recibidos del cliente se validan
contra un patrón antes de enviarse. Toda ejecución queda en `dba.bitacora_mantenimiento`.

**Por qué el usuario de la aplicación no es `sa`.**
`sql/04_permisos_monitoreo.sql` crea `dba_monitor` con `VIEW SERVER STATE`,
`VIEW ANY DEFINITION`, lectura sobre `msdb` y `ALTER` acotado al esquema de la aplicación.

---

## Limitaciones conocidas

- **SQL Server Express** no incluye el Agente SQL Server, por lo que el panel de jobs de
  respaldo aparecerá vacío. La consola lo informa en pantalla en lugar de fallar.
- Las estadísticas de `sys.dm_exec_query_stats` son **acumuladas desde el último reinicio** de
  la instancia o desde `DBCC FREEPROCCACHE`; no son un histórico permanente.
- `sys.dm_db_index_physical_stats` se ejecuta en modo `LIMITED` para no impactar el
  rendimiento de la instancia monitoreada.
- La aplicación no implementa autenticación de usuarios propia: se asume despliegue en red
  interna, tal como una herramienta de administración.

---

## Estructura del repositorio

```
dba-monitor/
├── sql/                      Scripts de instalación y catálogo de consultas
├── backend/                  Spring Boot (Java 25, patrón MVC por capas)
│   └── src/main/java/cr/ac/una/eif402/dbamonitor/
│       ├── config/           Propiedades externas y CORS
│       ├── common/           Repositorio base y manejo global de errores
│       ├── conexion/         Selección de instancia en tiempo de ejecución
│       ├── instancia/        Módulo 1
│       ├── rendimiento/      Módulo 2
│       ├── almacenamiento/   Módulo 3
│       ├── respaldo/         Módulo 4
│       ├── auditoria/        Módulo 5
│       └── mantenimiento/    Módulo 6
├── frontend/                 React + Vite
│   └── src/
│       ├── api/              Cliente HTTP
│       ├── components/       Componentes reutilizables
│       ├── hooks/            useRecurso (carga, error, refresco)
│       └── pages/            Una vista por módulo
└── docs/                     Reparto de trabajo y guía de defensa
```

---

## Endpoints

| Método | Ruta | Módulo |
|---|---|---|
| GET | `/api/conexion/perfiles` | Conexión |
| POST | `/api/conexion/perfil-activo` | Conexión |
| GET | `/api/instancia/estado` | M1 |
| GET | `/api/rendimiento/resumen` | M2 |
| GET | `/api/almacenamiento/resumen` | M3 |
| POST | `/api/almacenamiento/capturas` | M3 |
| GET | `/api/respaldos/resumen` | M4 |
| GET | `/api/auditoria/resumen` | M5 |
| GET | `/api/mantenimiento/resumen` | M6 |
| POST | `/api/mantenimiento/estadisticas` | M6 |
| POST | `/api/mantenimiento/validacion` | M6 |
| POST | `/api/mantenimiento/recompilacion` | M6 |

---

## Correcciones aplicadas tras la primera ejecución real

Estos dos problemas solo aparecieron al conectar la herramienta contra una
instancia real. Vale la pena poder explicarlos en la defensa.

### 1. Tipo `smallint` mapeado como `Short`, no como `Integer`

`sys.dm_exec_requests.blocking_session_id` es de tipo `smallint`, y el driver
JDBC lo entrega como `java.lang.Short`. El casteo directo a `Integer` lanzaba
`ClassCastException` y tumbaba todo el módulo 2.

La solución no es castear sino dejar que el driver convierta:

```java
rs.getObject("sesion_bloqueadora") == null ? null : rs.getInt("sesion_bloqueadora")
```

`getInt()` acepta cualquier tipo entero; la comprobación de nulo preserva el
caso «la sesión no está bloqueada». El mismo patrón se aplicó a todas las
columnas numéricas opcionales del proyecto.

### 2. Conflicto de intercalaciones en el `UNION ALL` de roles

Los principales de servidor (`sys.server_principals`) usan la intercalación de
la instancia; los de base de datos (`sys.database_principals`) usan la de la
base. Cuando ambas difieren —por ejemplo `SQL_Latin1_General_CP1_CI_AS` en el
servidor y `Modern_Spanish_CI_AI` en la base— el motor no sabe con qué reglas
comparar los textos y aborta con el error 451 al ordenar.

La solución es normalizar ambos lados en tiempo de consulta:

```sql
rol.name COLLATE DATABASE_DEFAULT AS rol
```

Se eligió `COLLATE DATABASE_DEFAULT` en lugar de fijar una intercalación
concreta porque la herramienta debe funcionar contra cualquier instancia sin
saber de antemano cómo está configurada. Tampoco se modifica la intercalación
del servidor ni la de la base: una herramienta de monitoreo no debe alterar la
configuración de lo que observa.

El mismo tratamiento se aplicó a las comparaciones entre `msdb.dbo.backupset`
y `sys.databases` de los módulos 3 y 4, que son vulnerables al mismo problema.
