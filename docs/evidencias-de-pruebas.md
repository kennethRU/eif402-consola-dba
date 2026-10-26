# Evidencias de las pruebas realizadas

**Proyecto:** Consola DBA para SQL Server · EIF402 · Grupo 2
**Entorno de prueba:** SQL Server 2022 Developer Edition en Docker (contenedor
`sqlserver2022`), macOS, JDK 21 compilando con `release 17`, Node.js, navegador en
`localhost:5173`.
**Base monitoreada:** `QuinielasDB` · 656 MB · 422.629 filas en `quiniela.Pronostico`
**Login de la aplicación:** `dba_monitor` (privilegios mínimos, no `sa`)

Las evidencias se guardan en `docs/evidencias/`. Los archivos `M1_instancia.pdf` a
`M6_mantenimiento.pdf` son capturas completas de cada módulo tomadas el 25 de octubre de
2026 entre las 7:23 y las 7:24 p. m.; las capturas de pruebas individuales usan el código de
la prueba como nombre (por ejemplo, `P-13.png`).

---

## 1. Integración y arranque

| Código | Prueba | Resultado esperado | Resultado obtenido | Estado |
|---|---|---|---|---|
| P-01 | Compilar y levantar el backend desde el repositorio clonado (`mvn clean spring-boot:run`) | Compila sin errores y arranca en el puerto 8080 | Compiló 35 archivos fuente y arrancó (25 oct 2026) | Aprobada |
| P-02 | Levantar el frontend (`npm run dev`) y abrir `localhost:5173` | La consola carga y muestra el menú de seis módulos | Cargó la consola con M1 a M6; la barra superior indica EN LÍNEA y la conexión `local · localhost:1433 / QuinielasDB` (`M1_instancia.pdf`) | Aprobada |
| P-03 | Abrir cada uno de los seis módulos | Cada módulo muestra datos reales de la instancia, sin errores | Los seis módulos mostraron información de `QuinielasDB` (`M1` a `M6_*.pdf`, 25 oct 2026) | Aprobada |
| P-04 | Conectar con el login `dba_monitor` en lugar de `sa` | Todos los módulos funcionan con privilegios mínimos | Funcionó tras agregar el permiso de la corrección C-03 | Aprobada |

---

## 2. Pruebas funcionales por módulo

| Código | Módulo | Prueba | Resultado esperado | Resultado obtenido | Estado |
|---|---|---|---|---|---|
| P-05 | M3 | Revisar el registro de transacciones | Se muestra el tamaño del log frente al de datos | Log de 520 MB frente a 136 MB de datos; filegroup PRIMARY al 62,64 %, nivel NORMAL (`M3_almacenamiento.pdf`) | Aprobada |
| P-06 | M4 | Evaluar el riesgo de respaldo de las bases | Nivel ALTO si no hay respaldo completo o si supera 72 h; aviso adicional en bases FULL sin respaldos de log | `QuinielasDB`: riesgo ALTO, último completo del 7 ago 2026 (hace 1 919 h), con diferencial y log del mismo día y ninguno posterior. Las demás bases de usuario no tienen respaldo completo: ALTO, con aviso de log en las que están en FULL (`M4_respaldos.pdf`) | Aprobada |
| P-07 | M5 | Auditar logins de instancia | Se señalan más de dos sysadmin y `sa` habilitada | 8 logins de instancia, 4 en sysadmin y cuenta `sa` habilitada, señalados en el diagnóstico (`M5_auditoria.pdf`) | Aprobada |
| P-08 | M6 | Analizar fragmentación de índices | Recomendación REBUILD para fragmentación ≥ 30 % | `PK_Pronostico` 99,96 % e `IX_Pronostico_Demo_Fragmentacion` 89,54 %: RECONSTRUIR; los otros dos índices por debajo de 1 %: sin acción (`M6_mantenimiento.pdf`) | Aprobada |
| P-09 | M1 | Verificar identificación de la instancia | Se muestran versión, edición, intercalación, estado y tiempo de actividad | SQL Server 2022 RTM-CU23, 16.0.4236.2, Developer Edition (64-bit), `SQL_Latin1_General_CP1_CI_AS`, ONLINE, 39 min de actividad, 10 CPU lógicas, 13 bases administradas (`M1_instancia.pdf`) | Aprobada |
| P-10 | M2 | Generar un bloqueo con dos sesiones (`BEGIN TRAN` + `UPDATE` sin confirmar, y un `SELECT` sobre la misma fila) | La sesión bloqueada aparece con su sesión bloqueadora; si supera 5 s se marca crítica | _Por completar_ | Pendiente |
| P-11 | M2 | Cambiar «Ordenar consultas por» y «Cantidad de filas» | La tabla se reordena y ajusta el número de filas | _Por completar_ | Pendiente |
| P-12 | M3 | Pulsar «Registrar captura» | Se inserta una fila en `dba.snapshot_almacenamiento` | _Por completar_ | Pendiente |
| P-13 | M5 | Detectar dependencia rota (bloque C de la demostración: vista sobre una tabla eliminada) | `quiniela.vw_DemoObjetoInvalido` aparece en «Objetos con dependencias rotas» | _Por completar_ | Pendiente |
| P-14 | M6 | «Validar objetos» con la vista rota | La vista queda registrada en «Objetos inválidos detectados» | _Por completar_ | Pendiente |
| P-15 | M6 | Recrear la tabla y pulsar «Recompilar pendientes» | La vista se corrige y desaparece de la lista; la acción queda en la bitácora | _Por completar_ | Pendiente |
| P-16 | M6 | «Recalcular estadísticas» sobre una tabla | La operación termina y queda en «Bitácora de mantenimiento» | _Por completar_ | Pendiente |
| P-17 | M6 | Escribir un nombre de tabla con caracteres no permitidos (por ejemplo `x; DROP`) | La solicitud se rechaza sin ejecutar nada en la base | _Por completar_ | Pendiente |

---

## 3. Defectos encontrados y corregidos

Estos defectos solo aparecieron al ejecutar la herramienta contra la instancia real. Cada uno
se reprodujo, se corrigió y se volvió a probar.

| Código | Módulo | Síntoma | Causa | Corrección | Reprueba |
|---|---|---|---|---|---|
| C-01 | M5 | La consulta de roles fallaba con el error 451 | Los principales de servidor usan `SQL_Latin1_General_CP1_CI_AS` y los de la base `Modern_Spanish_CI_AI`; el `UNION ALL` no podía comparar | `COLLATE DATABASE_DEFAULT` en ambos lados (también en las comparaciones de M3 y M4 con `msdb`) | Aprobada |
| C-02 | M2 | `ClassCastException` al cargar el módulo | `blocking_session_id` es `smallint` y el driver JDBC lo entrega como `Short` | Lectura con `rs.getInt()` y comprobación de nulo, aplicada a todas las columnas numéricas opcionales | Aprobada |
| C-03 | M5 | Error de permiso al leer dependencias con `dba_monitor` | `sys.sql_expression_dependencies` reside en `mssqlsystemresource` y no hereda `VIEW DEFINITION` | `GRANT SELECT` explícito sobre la vista | Aprobada |
| C-04 | M5 | `inserted` y `deleted` aparecían como dependencias rotas | Son pseudo-tablas que solo existen durante la ejecución de un trigger | Exclusión con `NOT IN (N'inserted', N'deleted')` | Aprobada |
| C-05 | M4 | Mensaje incorrecto cuando no hay jobs de respaldo | Texto del panel vacío | Corregido en `ModuloRespaldos` (commit `6522b5c`) | Aprobada: con el Agente en ejecución y sin jobs, el panel muestra el mensaje corregido (`M4_respaldos.pdf`) |

**Observación sobre los datos de prueba:** el trigger `tr_Pronostico_BloquearPartidoIniciado`
de `QuinielasDB` es `INSTEAD OF UPDATE` sobre toda la tabla y bloquea cualquier operación de
mantenimiento sobre `quiniela.Pronostico`. Se desactivó durante la carga de datos de prueba.

---

## 4. Requerimientos no funcionales

| Código | Requerimiento | Verificación | Estado |
|---|---|---|---|
| N-01 | Las credenciales no están en el código fuente | Se leen de variables de entorno; `sql/04_permisos_monitoreo.sql` usa el marcador `N'CAMBIAR_ANTES_DE_EJECUTAR'`; la contraseña real no aparece en el historial de Git | Aprobada |
| N-02 | Privilegios mínimos | La aplicación opera con `dba_monitor`, no con `sa` | Aprobada |
| N-03 | Sin SQL dinámico concatenado | Las acciones de M6 se invocan como `EXEC dba.usp_...` con parámetros; los identificadores se validan con un patrón | Aprobada (reforzada por P-17) |
| N-04 | Bajo impacto en la instancia monitoreada | `sys.dm_db_index_physical_stats` en modo `LIMITED`; solo índices de más de 100 páginas | Aprobada |
| N-05 | Cambio de instancia sin recompilar | Selector de conexión con los perfiles de `application.yml` | _Por completar_ |
