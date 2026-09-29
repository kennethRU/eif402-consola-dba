/* =====================================================================
   EIF402 - Proyecto Integrador
   Script 04: Login de monitoreo con privilegios mínimos

   La herramienta NO debe conectarse con 'sa'. Se crea un login dedicado
   con los permisos estrictamente necesarios (principio de mínimo
   privilegio), lo cual es también un criterio de la rúbrica.

   Configurado para: base QuinielasDB, esquema de aplicación "quiniela".
   Si monitorea otra base, cambie el USE y el nombre del esquema al final.

   IMPORTANTE: cambie la contraseña por una propia antes de ejecutar y NO
   la registre en el repositorio. La aplicación la lee desde una variable
   de entorno.
   ===================================================================== */

USE master;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'dba_monitor')
BEGIN
    CREATE LOGIN dba_monitor
        WITH PASSWORD = N'CAMBIAR_ANTES_DE_EJECUTAR',
             CHECK_POLICY = ON,
             DEFAULT_DATABASE = [master];
END
GO

/* Permisos a nivel de instancia -------------------------------------- */
GRANT VIEW SERVER STATE   TO dba_monitor;  -- DMVs: sesiones, memoria, esperas, query stats
GRANT VIEW ANY DATABASE   TO dba_monitor;  -- listar bases de datos
GRANT VIEW ANY DEFINITION TO dba_monitor;  -- metadatos de logins, roles y objetos
GO

/* Acceso a msdb para el historial de respaldos ----------------------- */
USE msdb;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'dba_monitor')
    CREATE USER dba_monitor FOR LOGIN dba_monitor;
GO
GRANT SELECT  ON msdb.dbo.backupset          TO dba_monitor;
GRANT SELECT  ON msdb.dbo.backupmediafamily  TO dba_monitor;
GRANT SELECT  ON msdb.dbo.sysjobs            TO dba_monitor;
GRANT SELECT  ON msdb.dbo.sysjobhistory      TO dba_monitor;
GRANT EXECUTE ON msdb.dbo.agent_datetime     TO dba_monitor;
GO

/* Acceso a la base de datos monitoreada ------------------------------ */
USE [QuinielasDB];
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'dba_monitor')
    CREATE USER dba_monitor FOR LOGIN dba_monitor;
GO

-- Metadatos y DMVs a nivel de base de datos.
GRANT VIEW DATABASE STATE TO dba_monitor;
GRANT VIEW DEFINITION     TO dba_monitor;
GO

-- sys.sql_expression_dependencies es una vista de catalogo que reside en la
-- base de recursos del sistema (mssqlsystemresource) y exige un GRANT SELECT
-- explicito sobre si misma. No basta con VIEW DEFINITION ni a nivel de
-- esquema ni a nivel de base de datos: sin esta linea el panel de
-- dependencias rotas del modulo 5 falla con el error 229.
GRANT SELECT ON sys.sql_expression_dependencies TO dba_monitor;
GO

-- Tablas y procedimientos de apoyo de la herramienta.
GRANT SELECT, INSERT, UPDATE ON SCHEMA::dba TO dba_monitor;
GRANT EXECUTE                ON SCHEMA::dba TO dba_monitor;
GO

-- Esquema de la aplicación monitoreada.
--   SELECT: sys.dm_db_stats_properties exige permiso sobre la tabla para
--           devolver las propiedades de sus estadísticas (módulo 6). Sin
--           este GRANT la DMV devuelve cero filas SIN generar error, que
--           es la peor forma de fallar: parece que funciona.
--   ALTER:  UPDATE STATISTICS y sp_refreshsqlmodule modifican el objeto.
--           Se acota al esquema de la aplicación, no a toda la base.
GRANT SELECT ON SCHEMA::quiniela TO dba_monitor;
GRANT ALTER  ON SCHEMA::quiniela TO dba_monitor;
GO

/* Verificación --------------------------------------------------------
   Las seis consultas deben responder. Si alguna falla, el GRANT
   correspondiente no se aplicó y ese módulo no funcionará.
   -------------------------------------------------------------------- */
EXECUTE AS LOGIN = N'dba_monitor';

SELECT SUSER_NAME()    AS usuario_actual;                       -- el login existe
SELECT TOP 1 cpu_count AS cpus        FROM sys.dm_os_sys_info;  -- VIEW SERVER STATE
SELECT COUNT(*)        AS bases       FROM sys.databases;       -- VIEW ANY DATABASE
SELECT COUNT(*)        AS respaldos   FROM msdb.dbo.backupset;  -- acceso a msdb
SELECT COUNT(*)        AS objetos_dba FROM dba.objeto_invalido; -- esquema dba

SELECT COUNT(*) AS estadisticas_visibles                        -- SELECT sobre el esquema
FROM sys.stats s
CROSS APPLY sys.dm_db_stats_properties(s.object_id, s.stats_id) sp
JOIN sys.objects o ON o.object_id = s.object_id
WHERE o.type = 'U' AND o.is_ms_shipped = 0;

REVERT;
GO

/* Limitación conocida -------------------------------------------------
   dba.usp_actualizar_estadisticas, cuando no recibe una tabla, ejecuta
   sp_updatestats sobre toda la base, lo cual exige pertenecer a db_owner.
   Con estos permisos el recálculo POR TABLA funciona y el de base
   completa falla por permisos.

   Esto es deliberado: dar db_owner a un usuario de monitoreo contradice
   el principio de mínimo privilegio. La operación amplia queda reservada
   al DBA.
   -------------------------------------------------------------------- */
