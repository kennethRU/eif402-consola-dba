/* =====================================================================
   EIF402 - Proyecto Integrador
   Script 03: Catálogo de consultas administrativas (solo lectura)

   Estas son las MISMAS consultas que ejecuta el backend. Se mantienen
   aquí para poder validarlas directamente en SSMS y para la defensa
   del proyecto. Cada bloque indica el módulo, la vista/DMV utilizada y
   el permiso requerido.
   ===================================================================== */


/* =====================================================================
   MÓDULO 1 - ESTADO GENERAL DE LA INSTANCIA
   ===================================================================== */

-- 1.1 Identificación de servidor, instancia, versión y arranque
-- DMV: sys.dm_os_sys_info | Permiso: VIEW SERVER STATE
SELECT
    CAST(SERVERPROPERTY('MachineName')    AS NVARCHAR(128)) AS nombre_servidor,
    CAST(SERVERPROPERTY('ServerName')     AS NVARCHAR(128)) AS nombre_completo_instancia,
    ISNULL(CAST(SERVERPROPERTY('InstanceName') AS NVARCHAR(128)), N'MSSQLSERVER') AS nombre_instancia,
    @@SERVICENAME                                           AS nombre_servicio,
    CAST(SERVERPROPERTY('Edition')        AS NVARCHAR(128)) AS edicion,
    CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(128)) AS version_producto,
    CAST(SERVERPROPERTY('ProductLevel')   AS NVARCHAR(128)) AS nivel_producto,
    CAST(SERVERPROPERTY('ProductUpdateLevel') AS NVARCHAR(128)) AS nivel_actualizacion,
    LEFT(@@VERSION, CHARINDEX(CHAR(10), @@VERSION + CHAR(10)) - 1) AS descripcion_version,
    CAST(SERVERPROPERTY('Collation')      AS NVARCHAR(128)) AS intercalacion,
    -- El estado de la instancia es implícito: si la consulta responde, está ONLINE.
    -- Se complementa con el estado del proceso a nivel de sistema operativo.
    N'ONLINE' AS estado_instancia,
    si.sqlserver_start_time                                 AS fecha_inicio,
    DATEDIFF(SECOND, si.sqlserver_start_time, SYSDATETIME()) AS segundos_actividad,
    si.cpu_count                                            AS cantidad_cpu,
    si.scheduler_count                                      AS cantidad_planificadores,
    CAST(si.physical_memory_kb   / 1024.0 AS DECIMAL(18,2)) AS memoria_fisica_mb,
    CAST(si.committed_target_kb  / 1024.0 AS DECIMAL(18,2)) AS memoria_objetivo_mb,
    CAST(si.committed_kb         / 1024.0 AS DECIMAL(18,2)) AS memoria_utilizada_mb
FROM sys.dm_os_sys_info si;


-- 1.2 Estado del servicio a nivel de sistema operativo (SQL Server 2008 R2 SP1+)
-- DMV: sys.dm_server_services | Permiso: VIEW SERVER STATE
SELECT servicename    AS nombre_servicio,
       status_desc    AS estado,
       startup_type_desc AS tipo_inicio,
       process_id     AS id_proceso,
       last_startup_time AS ultimo_arranque,
       service_account   AS cuenta_servicio
FROM sys.dm_server_services;


-- 1.3 Bases de datos administradas por la instancia
-- Vistas: sys.databases, sys.master_files | Permiso: VIEW ANY DATABASE
SELECT d.database_id           AS id_base_datos,
       d.name                  AS nombre,
       d.state_desc            AS estado,
       d.recovery_model_desc   AS modelo_recuperacion,
       d.compatibility_level   AS nivel_compatibilidad,
       d.collation_name        AS intercalacion,
       d.create_date           AS fecha_creacion,
       d.is_read_only          AS solo_lectura,
       d.user_access_desc      AS tipo_acceso,
       CAST(SUM(mf.size) * 8.0 / 1024 AS DECIMAL(18,2)) AS tamano_total_mb
FROM sys.databases d
LEFT JOIN sys.master_files mf ON mf.database_id = d.database_id
GROUP BY d.database_id, d.name, d.state_desc, d.recovery_model_desc,
         d.compatibility_level, d.collation_name, d.create_date,
         d.is_read_only, d.user_access_desc
ORDER BY d.name;


-- 1.4 Distribución de memoria por componente (clerks principales)
-- DMV: sys.dm_os_memory_clerks | Permiso: VIEW SERVER STATE
SELECT TOP (10)
       type                                          AS componente,
       CAST(SUM(pages_kb) / 1024.0 AS DECIMAL(18,2)) AS memoria_mb
FROM sys.dm_os_memory_clerks
GROUP BY type
HAVING SUM(pages_kb) > 0
ORDER BY SUM(pages_kb) DESC;


/* =====================================================================
   MÓDULO 2 - MONITOREO DE RENDIMIENTO
   ===================================================================== */

-- 2.1 Consultas con mayor consumo de recursos
-- DMV: sys.dm_exec_query_stats + sys.dm_exec_sql_text
-- Nota: el caché de planes se limpia al reiniciar la instancia o al
--       ejecutar DBCC FREEPROCCACHE. Los datos son acumulados desde
--       creation_time, no históricos permanentes.
SELECT TOP (20)
       DB_NAME(st.dbid)                                        AS base_datos,
       qs.execution_count                                      AS ejecuciones,
       CAST(qs.total_worker_time   / 1000.0 AS DECIMAL(18,2))  AS cpu_total_ms,
       CAST(qs.total_worker_time   / 1000.0 / qs.execution_count AS DECIMAL(18,2)) AS cpu_promedio_ms,
       CAST(qs.total_elapsed_time  / 1000.0 AS DECIMAL(18,2))  AS duracion_total_ms,
       CAST(qs.total_elapsed_time  / 1000.0 / qs.execution_count AS DECIMAL(18,2)) AS duracion_promedio_ms,
       qs.total_logical_reads                                  AS lecturas_logicas_total,
       qs.total_logical_reads / qs.execution_count             AS lecturas_logicas_promedio,
       qs.total_physical_reads                                 AS lecturas_fisicas_total,
       qs.total_logical_writes                                 AS escrituras_total,
       qs.creation_time                                        AS fecha_plan,
       qs.last_execution_time                                  AS ultima_ejecucion,
       SUBSTRING(st.text,
                 (qs.statement_start_offset / 2) + 1,
                 ((CASE qs.statement_end_offset
                        WHEN -1 THEN DATALENGTH(st.text)
                        ELSE qs.statement_end_offset
                   END - qs.statement_start_offset) / 2) + 1)  AS texto_consulta
FROM sys.dm_exec_query_stats qs
CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) st
WHERE qs.execution_count > 0
ORDER BY qs.total_worker_time DESC;   -- variantes: total_elapsed_time, total_logical_reads


-- 2.2 Resumen de tiempos de ejecución del caché de planes
SELECT COUNT(*)                                                     AS consultas_en_cache,
       SUM(qs.execution_count)                                      AS ejecuciones_totales,
       CAST(SUM(qs.total_elapsed_time) / 1000.0 AS DECIMAL(18,2))   AS duracion_acumulada_ms,
       CAST(SUM(qs.total_elapsed_time) / 1000.0
            / NULLIF(SUM(qs.execution_count), 0) AS DECIMAL(18,2))  AS duracion_promedio_ms,
       CAST(SUM(qs.total_worker_time)  / 1000.0 AS DECIMAL(18,2))   AS cpu_acumulado_ms
FROM sys.dm_exec_query_stats qs;


-- 2.3 Sesiones activas de usuario
-- DMV: sys.dm_exec_sessions, sys.dm_exec_connections, sys.dm_exec_requests
SELECT s.session_id                 AS id_sesion,
       s.login_name                 AS usuario,
       s.host_name                  AS equipo_cliente,
       s.program_name               AS aplicacion,
       s.status                     AS estado,
       DB_NAME(s.database_id)       AS base_datos,
       s.cpu_time                   AS cpu_ms,
       s.memory_usage * 8           AS memoria_kb,
       s.reads                      AS lecturas,
       s.writes                     AS escrituras,
       s.logical_reads              AS lecturas_logicas,
       s.login_time                 AS inicio_sesion,
       s.last_request_start_time    AS ultima_solicitud,
       c.client_net_address         AS direccion_cliente,
       r.blocking_session_id        AS sesion_bloqueadora,
       r.wait_type                  AS tipo_espera,
       r.wait_time                  AS tiempo_espera_ms,
       r.command                    AS comando_actual
FROM sys.dm_exec_sessions s
LEFT JOIN sys.dm_exec_connections c ON c.session_id = s.session_id
LEFT JOIN sys.dm_exec_requests    r ON r.session_id = s.session_id
WHERE s.is_user_process = 1
ORDER BY s.cpu_time DESC;


-- 2.4 Sesiones bloqueadas
SELECT r.session_id                 AS sesion_bloqueada,
       r.blocking_session_id        AS sesion_bloqueadora,
       r.wait_type                  AS tipo_espera,
       r.wait_time                  AS tiempo_espera_ms,
       r.wait_resource              AS recurso_esperado,
       r.status                     AS estado,
       r.command                    AS comando,
       DB_NAME(r.database_id)       AS base_datos,
       sb.login_name                AS usuario_bloqueador,
       sb.program_name              AS aplicacion_bloqueadora,
       tb.text                      AS consulta_bloqueada,
       tv.text                      AS consulta_bloqueadora
FROM sys.dm_exec_requests r
LEFT JOIN sys.dm_exec_sessions    sb ON sb.session_id = r.blocking_session_id
LEFT JOIN sys.dm_exec_connections cv ON cv.session_id = r.blocking_session_id
OUTER APPLY sys.dm_exec_sql_text(r.sql_handle)          tb
OUTER APPLY sys.dm_exec_sql_text(cv.most_recent_sql_handle) tv
WHERE r.blocking_session_id <> 0;


-- 2.5 Principales tipos de espera acumulados (diagnóstico de cuellos de botella)
SELECT TOP (10)
       wait_type                                          AS tipo_espera,
       waiting_tasks_count                                AS tareas_en_espera,
       CAST(wait_time_ms / 1000.0 AS DECIMAL(18,2))       AS espera_total_seg,
       CAST(signal_wait_time_ms / 1000.0 AS DECIMAL(18,2)) AS espera_cpu_seg,
       CAST(100.0 * wait_time_ms
            / NULLIF(SUM(wait_time_ms) OVER (), 0) AS DECIMAL(5,2)) AS porcentaje
FROM sys.dm_os_wait_stats
WHERE wait_type NOT IN (
        N'CLR_SEMAPHORE', N'LAZYWRITER_SLEEP', N'RESOURCE_QUEUE', N'SLEEP_TASK',
        N'SLEEP_SYSTEMTASK', N'SQLTRACE_BUFFER_FLUSH', N'WAITFOR', N'XE_TIMER_EVENT',
        N'BROKER_TASK_STOP', N'CHECKPOINT_QUEUE', N'REQUEST_FOR_DEADLOCK_SEARCH',
        N'XE_DISPATCHER_WAIT', N'BROKER_TO_FLUSH', N'DIRTY_PAGE_POLL', N'HADR_FILESTREAM_IOMGR_IOCOMPLETION',
        N'SP_SERVER_DIAGNOSTICS_SLEEP', N'QDS_PERSIST_TASK_MAIN_LOOP_SLEEP', N'QDS_SHUTDOWN_QUEUE')
  AND wait_time_ms > 0
ORDER BY wait_time_ms DESC;


/* =====================================================================
   MÓDULO 3 - GESTIÓN DEL ALMACENAMIENTO
   ===================================================================== */

-- 3.1 Espacio por filegroup
SELECT fg.name                                                   AS filegroup,
       fg.type_desc                                              AS tipo,
       fg.is_default                                             AS es_predeterminado,
       COUNT(df.file_id)                                         AS cantidad_archivos,
       CAST(SUM(df.size) * 8.0 / 1024 AS DECIMAL(18,2))          AS tamano_mb,
       CAST(SUM(CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT)) * 8.0 / 1024 AS DECIMAL(18,2)) AS usado_mb,
       CAST((SUM(df.size) - SUM(CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT))) * 8.0 / 1024 AS DECIMAL(18,2)) AS libre_mb,
       CAST(100.0 * SUM(CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT))
            / NULLIF(SUM(df.size), 0) AS DECIMAL(5,2))           AS porcentaje_uso
FROM sys.filegroups fg
JOIN sys.database_files df ON df.data_space_id = fg.data_space_id
GROUP BY fg.name, fg.type_desc, fg.is_default;


-- 3.2 Archivos de datos y log
SELECT df.file_id                                                AS id_archivo,
       df.name                                                   AS nombre_logico,
       df.physical_name                                          AS ruta_fisica,
       df.type_desc                                              AS tipo,
       fg.name                                                   AS filegroup,
       df.state_desc                                             AS estado,
       CAST(df.size * 8.0 / 1024 AS DECIMAL(18,2))               AS tamano_mb,
       CAST(CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT) * 8.0 / 1024 AS DECIMAL(18,2)) AS usado_mb,
       CAST((df.size - CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT)) * 8.0 / 1024 AS DECIMAL(18,2)) AS libre_mb,
       CASE df.max_size WHEN -1 THEN N'Sin límite'
                        WHEN 268435456 THEN N'2 TB (log)'
                        ELSE CONCAT(CAST(df.max_size * 8.0 / 1024 AS DECIMAL(18,2)), N' MB') END AS tamano_maximo,
       CASE WHEN df.is_percent_growth = 1 THEN CONCAT(df.growth, N' %')
            ELSE CONCAT(CAST(df.growth * 8.0 / 1024 AS DECIMAL(18,2)), N' MB') END AS crecimiento_configurado
FROM sys.database_files df
LEFT JOIN sys.filegroups fg ON fg.data_space_id = df.data_space_id;


-- 3.3 Tamaño actual de la base de datos
SELECT DB_NAME()                                                                    AS base_datos,
       CAST(SUM(size) * 8.0 / 1024 AS DECIMAL(18,2))                                AS tamano_total_mb,
       CAST(SUM(CASE WHEN type = 0 THEN size ELSE 0 END) * 8.0 / 1024 AS DECIMAL(18,2)) AS datos_mb,
       CAST(SUM(CASE WHEN type = 1 THEN size ELSE 0 END) * 8.0 / 1024 AS DECIMAL(18,2)) AS log_mb,
       CAST(SUM(CAST(FILEPROPERTY(name,'SpaceUsed') AS BIGINT)) * 8.0 / 1024 AS DECIMAL(18,2)) AS usado_mb
FROM sys.database_files;


-- 3.4 Objetos con mayor consumo de almacenamiento
-- DMV: sys.dm_db_partition_stats (no requiere recorrer las tablas)
SELECT TOP (20)
       sch.name                                                        AS esquema,
       t.name                                                          AS objeto,
       CAST(SUM(ps.reserved_page_count) * 8.0 / 1024 AS DECIMAL(18,2)) AS reservado_mb,
       CAST(SUM(ps.used_page_count)     * 8.0 / 1024 AS DECIMAL(18,2)) AS usado_mb,
       SUM(CASE WHEN ps.index_id IN (0,1) THEN ps.row_count ELSE 0 END) AS filas,
       (SELECT COUNT(*) FROM sys.indexes i
         WHERE i.object_id = t.object_id AND i.index_id > 0)           AS cantidad_indices
FROM sys.dm_db_partition_stats ps
JOIN sys.tables  t   ON t.object_id  = ps.object_id
JOIN sys.schemas sch ON sch.schema_id = t.schema_id
WHERE t.is_ms_shipped = 0
GROUP BY sch.name, t.name, t.object_id
ORDER BY SUM(ps.reserved_page_count) DESC;


-- 3.5 Crecimiento histórico de la base de datos
--     (a) desde la bitácora propia de la herramienta
SELECT CAST(fecha_captura AS DATE)                       AS fecha,
       CAST(SUM(tamano_mb) AS DECIMAL(18,2))             AS tamano_total_mb,
       CAST(SUM(usado_mb)  AS DECIMAL(18,2))             AS usado_mb
FROM dba.snapshot_almacenamiento
WHERE nombre_base_datos = DB_NAME()
GROUP BY CAST(fecha_captura AS DATE)
ORDER BY fecha;

--     (b) fuente alternativa real: tamaño de los respaldos históricos
SELECT CAST(bs.backup_start_date AS DATE)                          AS fecha,
       CAST(MAX(bs.backup_size) / 1024.0 / 1024 AS DECIMAL(18,2))  AS tamano_respaldo_mb
FROM msdb.dbo.backupset bs
WHERE bs.database_name COLLATE DATABASE_DEFAULT = DB_NAME() AND bs.type = 'D'
GROUP BY CAST(bs.backup_start_date AS DATE)
ORDER BY fecha;


/* =====================================================================
   MÓDULO 4 - RESPALDO Y RECUPERACIÓN
   ===================================================================== */

-- 4.1 Historial de respaldos
-- Tablas: msdb.dbo.backupset, msdb.dbo.backupmediafamily
-- Importante: backupset SOLO registra respaldos que finalizaron con éxito.
-- Los respaldos fallidos se consultan en el historial de jobs (4.3).
SELECT TOP (50)
       bs.database_name                                              AS base_datos,
       bs.backup_start_date                                          AS inicio,
       bs.backup_finish_date                                         AS fin,
       DATEDIFF(SECOND, bs.backup_start_date, bs.backup_finish_date) AS duracion_seg,
       CASE bs.type WHEN 'D' THEN N'Completo'
                    WHEN 'I' THEN N'Diferencial'
                    WHEN 'L' THEN N'Log de transacciones'
                    WHEN 'F' THEN N'Archivo o filegroup'
                    WHEN 'G' THEN N'Diferencial de archivo'
                    WHEN 'P' THEN N'Parcial'
                    WHEN 'Q' THEN N'Diferencial parcial'
                    ELSE N'Otro' END                                 AS tipo_respaldo,
       bs.recovery_model                                             AS modelo_recuperacion,
       CAST(bs.backup_size / 1024.0 / 1024 AS DECIMAL(18,2))         AS tamano_mb,
       CAST(bs.compressed_backup_size / 1024.0 / 1024 AS DECIMAL(18,2)) AS tamano_comprimido_mb,
       bs.is_copy_only                                               AS solo_copia,
       bs.user_name                                                  AS usuario,
       bs.server_name                                                AS servidor,
       mf.physical_device_name                                       AS dispositivo,
       N'Exitoso'                                                    AS estado
FROM msdb.dbo.backupset bs
LEFT JOIN msdb.dbo.backupmediafamily mf ON mf.media_set_id = bs.media_set_id
ORDER BY bs.backup_finish_date DESC;


-- 4.2 Último respaldo exitoso por base de datos y tipo (indicador de riesgo)
SELECT d.name                                                         AS base_datos,
       d.recovery_model_desc                                          AS modelo_recuperacion,
       MAX(CASE WHEN bs.type = 'D' THEN bs.backup_finish_date END)    AS ultimo_completo,
       MAX(CASE WHEN bs.type = 'I' THEN bs.backup_finish_date END)    AS ultimo_diferencial,
       MAX(CASE WHEN bs.type = 'L' THEN bs.backup_finish_date END)    AS ultimo_log,
       DATEDIFF(HOUR,
                MAX(CASE WHEN bs.type = 'D' THEN bs.backup_finish_date END),
                SYSDATETIME())                                        AS horas_desde_completo
FROM sys.databases d
LEFT JOIN msdb.dbo.backupset bs
       ON bs.database_name COLLATE DATABASE_DEFAULT
        = d.name           COLLATE DATABASE_DEFAULT
WHERE d.database_id <> 2   -- se excluye tempdb: no se respalda
GROUP BY d.name, d.recovery_model_desc
ORDER BY d.name;


-- 4.3 Historial de jobs de respaldo (permite detectar fallos)
SELECT TOP (25)
       j.name                                        AS nombre_job,
       h.step_name                                   AS paso,
       msdb.dbo.agent_datetime(h.run_date, h.run_time) AS fecha_ejecucion,
       CASE h.run_status WHEN 0 THEN N'Fallido'
                         WHEN 1 THEN N'Exitoso'
                         WHEN 2 THEN N'Reintento'
                         WHEN 3 THEN N'Cancelado'
                         ELSE N'En progreso' END     AS estado,
       h.message                                     AS mensaje
FROM msdb.dbo.sysjobhistory h
JOIN msdb.dbo.sysjobs j ON j.job_id = h.job_id
WHERE j.name LIKE N'%backup%' OR j.name LIKE N'%respaldo%'
ORDER BY h.run_date DESC, h.run_time DESC;


/* =====================================================================
   MÓDULO 5 - AUDITORÍA
   ===================================================================== */

-- 5.1 Logins registrados a nivel de instancia
SELECT sp.name                       AS login,
       sp.type_desc                  AS tipo,
       sp.is_disabled                AS deshabilitado,
       sp.create_date                AS fecha_creacion,
       sp.modify_date                AS fecha_modificacion,
       sp.default_database_name      AS base_datos_predeterminada,
       IS_SRVROLEMEMBER('sysadmin', sp.name) AS es_sysadmin
FROM sys.server_principals sp
WHERE sp.type IN ('S','U','G')   -- SQL, Windows, grupo de Windows
  AND sp.name NOT LIKE '##%'
ORDER BY sp.name;


-- 5.2 Usuarios de la base de datos actual
SELECT dp.name                       AS usuario,
       dp.type_desc                  AS tipo,
       dp.create_date                AS fecha_creacion,
       dp.default_schema_name        AS esquema_predeterminado,
       sp.name                       AS login_asociado,
       dp.authentication_type_desc   AS tipo_autenticacion
FROM sys.database_principals dp
LEFT JOIN sys.server_principals sp ON sp.sid = dp.sid
WHERE dp.type IN ('S','U','G')
  AND dp.name NOT IN ('guest','INFORMATION_SCHEMA','sys')
ORDER BY dp.name;


-- 5.3 Roles y sus miembros (nivel base de datos y nivel servidor)
--
-- Nota sobre intercalaciones: los principales de servidor se almacenan con la
-- intercalacion de la instancia y los de base de datos con la de la base. Si
-- ambas difieren, el UNION ALL falla con el error 451 al ordenar por una
-- columna de texto. COLLATE DATABASE_DEFAULT normaliza ambos lados en tiempo
-- de consulta, sin modificar la configuracion del servidor ni de la base.
SELECT N'Base de datos'                        AS ambito,
       rol.name          COLLATE DATABASE_DEFAULT AS rol,
       miembro.name      COLLATE DATABASE_DEFAULT AS miembro,
       miembro.type_desc COLLATE DATABASE_DEFAULT AS tipo_miembro
FROM sys.database_role_members drm
JOIN sys.database_principals rol     ON rol.principal_id     = drm.role_principal_id
JOIN sys.database_principals miembro ON miembro.principal_id = drm.member_principal_id
UNION ALL
SELECT N'Servidor',
       rol.name          COLLATE DATABASE_DEFAULT,
       miembro.name      COLLATE DATABASE_DEFAULT,
       miembro.type_desc COLLATE DATABASE_DEFAULT
FROM sys.server_role_members srm
JOIN sys.server_principals rol     ON rol.principal_id     = srm.role_principal_id
JOIN sys.server_principals miembro ON miembro.principal_id = srm.member_principal_id
ORDER BY ambito, rol, miembro;


-- 5.4 Privilegios asignados en la base de datos
SELECT pr.name                                    AS beneficiario,
       pr.type_desc                               AS tipo_beneficiario,
       pe.state_desc                              AS estado,      -- GRANT / DENY / GRANT_WITH_GRANT
       pe.permission_name                         AS privilegio,
       pe.class_desc                              AS clase,
       CASE pe.class
            WHEN 0 THEN DB_NAME()
            WHEN 1 THEN OBJECT_SCHEMA_NAME(pe.major_id) + N'.' + OBJECT_NAME(pe.major_id)
            WHEN 3 THEN SCHEMA_NAME(pe.major_id)
            ELSE CAST(pe.major_id AS NVARCHAR(20))
       END                                        AS objeto
FROM sys.database_permissions pe
JOIN sys.database_principals pr ON pr.principal_id = pe.grantee_principal_id
WHERE pr.name NOT IN ('public','guest')
ORDER BY pr.name, pe.permission_name;


-- 5.5 Objetos con dependencias rotas (equivalente a objetos inválidos)
SELECT OBJECT_SCHEMA_NAME(sed.referencing_id)     AS esquema,
       OBJECT_NAME(sed.referencing_id)            AS objeto,
       o.type_desc                                AS tipo_objeto,
       ISNULL(sed.referenced_schema_name, N'dbo') AS esquema_referenciado,
       sed.referenced_entity_name                 AS entidad_referenciada,
       N'La entidad referenciada no existe en la base de datos' AS diagnostico
FROM sys.sql_expression_dependencies sed
JOIN sys.objects o ON o.object_id = sed.referencing_id
WHERE sed.referenced_id IS NULL
  AND sed.is_ambiguous = 0
  AND sed.referenced_server_name IS NULL
  AND sed.referenced_database_name IS NULL
  AND OBJECT_ID(QUOTENAME(ISNULL(sed.referenced_schema_name, N'dbo')) + N'.'
                + QUOTENAME(sed.referenced_entity_name)) IS NULL
  AND o.is_ms_shipped = 0;


/* =====================================================================
   MÓDULO 6 - MANTENIMIENTO PREVENTIVO
   ===================================================================== */

-- 6.1 Estadísticas desactualizadas
SELECT TOP (30)
       SCHEMA_NAME(o.schema_id)               AS esquema,
       o.name                                 AS tabla,
       s.name                                 AS estadistica,
       sp.last_updated                        AS ultima_actualizacion,
       sp.rows                                AS filas,
       sp.rows_sampled                        AS filas_muestreadas,
       sp.modification_counter                AS modificaciones_pendientes,
       DATEDIFF(DAY, sp.last_updated, SYSDATETIME()) AS dias_sin_actualizar
FROM sys.stats s
JOIN sys.objects o ON o.object_id = s.object_id
CROSS APPLY sys.dm_db_stats_properties(s.object_id, s.stats_id) sp
WHERE o.is_ms_shipped = 0 AND o.type = 'U'
ORDER BY sp.modification_counter DESC, sp.last_updated ASC;


-- 6.2 Fragmentación de índices (modo LIMITED: bajo costo)
SELECT TOP (30)
       SCHEMA_NAME(o.schema_id)                          AS esquema,
       o.name                                            AS tabla,
       i.name                                            AS indice,
       i.type_desc                                       AS tipo_indice,
       CAST(ips.avg_fragmentation_in_percent AS DECIMAL(5,2)) AS fragmentacion_porcentaje,
       ips.page_count                                    AS paginas,
       CASE WHEN ips.avg_fragmentation_in_percent >= 30 THEN N'Reconstruir (REBUILD)'
            WHEN ips.avg_fragmentation_in_percent >= 10 THEN N'Reorganizar (REORGANIZE)'
            ELSE N'Sin acción' END                       AS recomendacion
FROM sys.dm_db_index_physical_stats(DB_ID(), NULL, NULL, NULL, 'LIMITED') ips
JOIN sys.indexes i ON i.object_id = ips.object_id AND i.index_id = ips.index_id
JOIN sys.objects o ON o.object_id = ips.object_id
WHERE ips.page_count > 100 AND i.name IS NOT NULL AND o.is_ms_shipped = 0
ORDER BY ips.avg_fragmentation_in_percent DESC;


-- 6.3 Bitácora de operaciones ejecutadas desde la herramienta
SELECT TOP (50)
       fecha_ejecucion, tipo_operacion, objeto_afectado,
       resultado, mensaje, duracion_ms, usuario_solicitante
FROM dba.bitacora_mantenimiento
ORDER BY fecha_ejecucion DESC;
