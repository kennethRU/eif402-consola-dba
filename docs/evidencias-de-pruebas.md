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
| P-10 | M2 | Generar un bloqueo con dos sesiones de DBeaver: `BEGIN TRAN` + `UPDATE` real sobre `quiniela.Rol` sin confirmar, y `SELECT` de la misma fila desde otra sesión | La sesión bloqueada aparece con su sesión bloqueadora; si supera 5 s se marca crítica | Sesión 78 bloqueada por la 67 (`sa`), espera `LCK_M_S` de 36,62 s sobre un recurso `KEY`, con la sentencia bloqueada visible; diagnóstico: «1 sesión(es) bloqueada(s), de las cuales 1 superan los 5 segundos». Tras el `ROLLBACK`, la fila volvió a su valor original (`P-10_M2_bloqueo.pdf`) | Aprobada |
| P-11 | M2 | Ordenar por «Lecturas lógicas» con 10 filas | La tabla muestra 10 filas ordenadas por lecturas | 10 filas ordenadas por lecturas lógicas **totales** (ejecuciones × promedio: 178 238, 16 080, 14 412…); la tabla muestra la columna de promedio, por eso el orden no es evidente a simple vista (`P-11_M2_ordenamiento.pdf`) | Aprobada |
| P-12 | M3 | Pulsar «Registrar captura» | Se insertan filas en `dba.snapshot_almacenamiento` | «Captura registrada: 2 archivo(s)»; la tabla pasó de 0 a 2 filas (datos y log). La curva de crecimiento requiere capturas en días distintos (`P-12_M3_captura.pdf`) | Aprobada |
| P-13 | M5 | Detectar dependencia rota (bloque C de la demostración: vista sobre una tabla eliminada) | `quiniela.vw_DemoObjetoInvalido` aparece en «Objetos con dependencias rotas» | La vista aparece referenciando `quiniela.TablaDemo` con el diagnóstico «La entidad referenciada no existe»; «Objetos con problemas» pasó de 0 a 1 (`P-13_M5_dependencia_rota.pdf`) | Aprobada |
| P-14 | M6 | «Validar objetos» con la vista rota | La vista queda registrada en «Objetos inválidos detectados» | «Se detectaron 1 objeto(s)»: error 208 *Invalid object name*; «Objetos inválidos» = 1; bitácora: VALIDACION · EXITOSO · 80 ms (`P-14_M6_validacion.pdf`) | Aprobada |
| P-15 | M6 | Recrear la tabla y pulsar «Recompilar pendientes» | La vista se corrige y desaparece de la lista; la acción queda en la bitácora | «Recompilados 1 de 1 objeto(s)»; «Objetos inválidos» = 0; bitácora: RECOMPILACION · EXITOSO · 20 ms (`P-15_M6_recompilacion.pdf`) | Aprobada |
| P-16 | M6 | «Recalcular estadísticas» sobre `quiniela.Rol` | La operación termina y queda en «Bitácora de mantenimiento» | Ejecutó `UPDATE STATISTICS [quiniela].[Rol];` en 27 ms; bitácora: ESTADISTICAS [quiniela].[Rol] · EXITOSO; `PK_Rol` salió de la lista de desactualizadas (`P-16_M6_estadisticas.pdf`) | Aprobada |
| P-17 | M6 | Escribir `Rol;--` como nombre de tabla | La solicitud se rechaza sin ejecutar nada en la base | «El nombre de tabla 'Rol;--' no es un identificador válido»; ninguna fila nueva en la bitácora (`P-17_M6_identificador_invalido.pdf`) | Aprobada |
| P-19 | M1 | Verificar la hora de inicio tras la corrección O-01 | «Inicio» coincide con el último arranque del panel de servicios | Ambos indican 25/10/2026, 06:44 p. m. (`P-19_M1_hora_local.pdf`) | Aprobada |
| P-20 | M5 | Verificar el detalle de sysadmin tras la corrección O-02 | Se distingue la pertenencia directa de la pertenencia por grupo | `NT AUTHORITY\SYSTEM` aparece como «Sí (vía grupo)»; diagnóstico: «4 logins tienen privilegios sysadmin (1 por pertenencia a grupo)» (`P-20_M5_sysadmin.pdf`) | Aprobada |
| P-18 | M6 | Escribir un esquema inexistente (`quinela`) | Error controlado, sin caída de la consola | Mensaje «operación fallida: la instancia rechazó la consulta administrativa»; la consola sigue operando. Ver observaciones O-04 y O-05 (`P-18_M6_esquema_inexistente.pdf`) | Aprobada |

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
| N-05 | Cambio de instancia sin recompilar | Selector de conexión con los perfiles de `application.yml` | No ejecutada: el entorno de prueba tiene una sola instancia configurada; requiere una segunda instancia con el esquema `dba` y el login `dba_monitor` |

---

## 5. Observaciones detectadas durante las pruebas

Comportamientos que no impiden el funcionamiento pero conviene conocer para la defensa o
corregir en una versión posterior.

| Código | Módulo | Observación |
|---|---|---|
| O-01 | Todos | **Corregida.** Las horas leídas del servidor se mostraban en UTC, la zona del contenedor, en lugar de la hora local (UTC−6): M1 indicaba inicio «26/10, 12:44 a. m.» y el panel de servicios «25/10, 06:44 p. m.». Ahora el backend consulta el desfase del servidor (`DATEDIFF(MINUTE, GETUTCDATE(), GETDATE())`) y envía las fechas con zona horaria; verificado en P-19. |
| O-02 | M5 | **Corregida.** El indicador contaba 4 sysadmin (pertenencia efectiva, `IS_SRVROLEMEMBER`) y el panel de roles listaba 3 (solo asignaciones directas). Se conserva el conteo efectivo y ahora la consola distingue «Sí» de «Sí (vía grupo)», con una nota en el panel de roles; verificado en P-20. |
| O-03 | M2 | El orden por lecturas, CPU o duración usa los valores acumulados, pero la tabla muestra promedios por ejecución; el orden es correcto aunque no lo parezca a simple vista. |
| O-04 | M6 | Una operación que SQL Server rechaza (P-18) no se registra en `dba.bitacora_mantenimiento`; solo se registran las que llegan a ejecutarse. |
| O-05 | M6 | El mensaje de error ante un esquema inexistente es genérico y no indica la causa. |
| O-06 | Pruebas | Un `UPDATE` que asigna a una columna su mismo valor no genera bloqueo visible para los lectores: SQL Server no marca la página como modificada. Para reproducir P-10 hay que cambiar el dato de verdad y deshacerlo con `ROLLBACK`. |
