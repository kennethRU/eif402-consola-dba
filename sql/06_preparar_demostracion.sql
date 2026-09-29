/* =====================================================================
   EIF402 - Proyecto Integrador
   Script 06: Preparación de la demostración

   Ejecutar DESPUÉS de 05_datos_prueba.sql y poco antes de grabar el
   video o presentar. Genera las condiciones que cada módulo necesita
   mostrar. Los bloques son independientes: ejecute solo los que necesite.
   ===================================================================== */

USE QuinielasDB;
GO
SET NOCOUNT ON;
GO

/* =====================================================================
   BLOQUE A - Respaldos (Módulo 4)

   Sin esto, el módulo 4 muestra riesgo ALTO en todas las bases y una
   tabla vacía. Con esto tiene historial de los tres tipos de respaldo.

   La ruta /var/opt/mssql/backup es INTERNA del contenedor. Si la carpeta
   no existe, créela primero desde la terminal del host:
       docker exec -u root sqlserver2022 mkdir -p /var/opt/mssql/backup
       docker exec -u root sqlserver2022 chown mssql /var/opt/mssql/backup
   ===================================================================== */

-- A.1 Respaldo completo
BACKUP DATABASE QuinielasDB
TO DISK = '/var/opt/mssql/backup/QuinielasDB_full.bak'
WITH INIT, NAME = 'QuinielasDB - Respaldo completo', STATS = 25;
GO

-- A.2 Respaldo del log de transacciones
--     Necesario en modelo FULL: sin esto el archivo de log crece sin
--     límite. Es el hallazgo que el módulo 4 debe poder señalar.
BACKUP LOG QuinielasDB
TO DISK = '/var/opt/mssql/backup/QuinielasDB_log.trn'
WITH INIT, NAME = 'QuinielasDB - Respaldo de log', STATS = 25;
GO

-- A.3 Respaldo diferencial
BACKUP DATABASE QuinielasDB
TO DISK = '/var/opt/mssql/backup/QuinielasDB_diff.bak'
WITH DIFFERENTIAL, INIT, NAME = 'QuinielasDB - Diferencial', STATS = 25;
GO

-- A.4 Verificar que quedaron registrados
SELECT TOP 10
       database_name,
       CASE type WHEN 'D' THEN 'Completo'
                 WHEN 'I' THEN 'Diferencial'
                 WHEN 'L' THEN 'Log' END        AS tipo,
       backup_finish_date                       AS finalizado,
       CAST(backup_size / 1024.0 / 1024 AS DECIMAL(10,2)) AS tamano_mb
FROM msdb.dbo.backupset
WHERE database_name = 'QuinielasDB'
ORDER BY backup_finish_date DESC;
GO


/* =====================================================================
   BLOQUE B - Fragmentación y estadísticas desactualizadas (Módulo 6)

   Un INSERT masivo deja los índices ordenados y las estadísticas frescas.
   Estas operaciones provocan fragmentación real: borrados dispersos
   seguidos de inserciones intercaladas.
   ===================================================================== */

-- B.1 Borrado disperso: deja huecos en las páginas del índice agrupado
DELETE FROM quiniela.Pronostico
WHERE id_pronostico % 7 = 0;
GO

-- B.2 Actualizaciones masivas: incrementan el contador de modificaciones
--     de las estadísticas sin recalcularlas
UPDATE quiniela.Pronostico
   SET puntos_obtenidos = CASE WHEN puntos_obtenidos > 0 THEN 0 ELSE 1 END
WHERE id_pronostico % 5 = 0;
GO

UPDATE quiniela.Participacion
   SET estado_pago = 'Pagado'
WHERE estado_pago = 'Pendiente' AND id_participacion % 3 = 0;
GO

-- B.3 Comprobar que hay fragmentación para mostrar
SELECT TOP 10
       OBJECT_NAME(ips.object_id)                             AS tabla,
       i.name                                                 AS indice,
       CAST(ips.avg_fragmentation_in_percent AS DECIMAL(5,2)) AS fragmentacion,
       ips.page_count                                         AS paginas
FROM sys.dm_db_index_physical_stats(DB_ID(), NULL, NULL, NULL, 'LIMITED') ips
JOIN sys.indexes i ON i.object_id = ips.object_id AND i.index_id = ips.index_id
WHERE ips.page_count > 100 AND i.name IS NOT NULL
ORDER BY ips.avg_fragmentation_in_percent DESC;
GO

-- B.4 Comprobar estadísticas con modificaciones pendientes
SELECT TOP 10
       OBJECT_NAME(s.object_id)   AS tabla,
       s.name                     AS estadistica,
       sp.last_updated            AS ultima_actualizacion,
       sp.modification_counter    AS modificaciones_pendientes
FROM sys.stats s
CROSS APPLY sys.dm_db_stats_properties(s.object_id, s.stats_id) sp
JOIN sys.objects o ON o.object_id = s.object_id
WHERE o.is_ms_shipped = 0 AND o.type = 'U'
ORDER BY sp.modification_counter DESC;
GO


/* =====================================================================
   BLOQUE C - Objeto inválido a propósito (Módulos 5 y 6)

   Este es el bloque que demuestra la diferencia entre Oracle y SQL Server.
   Se crea una vista que depende de una tabla, y luego se rompe esa
   dependencia. La vista sigue existiendo en sys.objects: solo falla al
   ejecutarse. Eso es lo que detecta dba.usp_validar_modulos.
   ===================================================================== */

-- C.1 Tabla y vista de demostración
IF OBJECT_ID('quiniela.TablaTemporalDemo', 'U') IS NOT NULL
    DROP TABLE quiniela.TablaTemporalDemo;
GO

CREATE TABLE quiniela.TablaTemporalDemo
(
    id          INT IDENTITY(1,1) PRIMARY KEY,
    descripcion VARCHAR(100) NOT NULL
);
GO

INSERT INTO quiniela.TablaTemporalDemo (descripcion)
VALUES ('Fila de demostración 1'), ('Fila de demostración 2');
GO

CREATE OR ALTER VIEW quiniela.vw_DemoObjetoInvalido
AS
SELECT id, descripcion
FROM quiniela.TablaTemporalDemo;
GO

-- C.2 La vista funciona en este momento
SELECT * FROM quiniela.vw_DemoObjetoInvalido;
GO

-- C.3 Romper la dependencia eliminando la tabla base.
--     SQL Server NO marca la vista como inválida: sigue en sys.objects.
DROP TABLE quiniela.TablaTemporalDemo;
GO

-- C.4 Confirmar que la vista sigue "existiendo" pese a estar rota
SELECT name, type_desc, create_date
FROM sys.objects
WHERE name = 'vw_DemoObjetoInvalido';
GO

-- C.5 Al ejecutarla falla. Descomente para verlo en la defensa:
-- SELECT * FROM quiniela.vw_DemoObjetoInvalido;
GO

/*  A partir de aquí, en la consola web:
      1. Módulo 5 -> el panel "Objetos con dependencias rotas" muestra la vista
      2. Módulo 6 -> botón "Validar objetos" la registra como inválida
      3. Recree la tabla (bloque C.1) y use "Recompilar pendientes"
         para demostrar la corrección                                       */


/* =====================================================================
   BLOQUE D - Bloqueo visible entre sesiones (Módulo 2)

   NO ejecutar todo de una vez: requiere DOS pestañas de consulta.
   ===================================================================== */

/*  --- Pestaña 1: abrir la transacción y NO confirmarla ---

    BEGIN TRANSACTION;
    UPDATE quiniela.Participacion
       SET puntaje_total = puntaje_total + 1
     WHERE id_participacion = 1;
    -- dejar así, sin COMMIT

    --- Pestaña 2: intentar leer la misma fila (queda bloqueada) ---

    SELECT * FROM quiniela.Participacion WHERE id_participacion = 1;

    --- Ahora el módulo 2 muestra la sesión bloqueada y la bloqueadora ---
    --- Al terminar, en la pestaña 1: ---

    ROLLBACK TRANSACTION;
*/


/* =====================================================================
   BLOQUE E - Carga de trabajo para el caché de planes (Módulo 2)

   Consultas costosas deliberadas, para que el módulo 2 tenga qué mostrar
   en "Consultas de mayor consumo".
   ===================================================================== */

-- E.1 Agregación pesada sobre la tabla de hechos
SELECT TOP 20
       u.nombre_completo,
       COUNT(*)                    AS pronosticos,
       SUM(pr.puntos_obtenidos)    AS puntos,
       AVG(CAST(pr.goles_local_predichos AS DECIMAL(5,2))) AS promedio_goles
FROM quiniela.Pronostico pr
JOIN quiniela.Usuario u ON u.id_usuario = pr.id_usuario
JOIN quiniela.Partido pt ON pt.id_partido = pr.id_partido
WHERE pt.estado = 'Finalizado'
GROUP BY u.nombre_completo
ORDER BY SUM(pr.puntos_obtenidos) DESC;
GO 5   -- se ejecuta 5 veces para acumular estadísticas

-- E.2 Consulta deliberadamente ineficiente: función sobre la columna
--     del WHERE, lo que impide usar índices (SARGability)
SELECT COUNT(*)
FROM quiniela.Pronostico
WHERE YEAR(fecha_ingreso) = YEAR(GETDATE()) - 1;
GO 3

-- E.3 Producto cartesiano parcial: muchas lecturas lógicas
SELECT COUNT(*)
FROM quiniela.Pronostico pr1
JOIN quiniela.Pronostico pr2
  ON pr1.id_partido = pr2.id_partido
 AND pr1.goles_local_predichos = pr2.goles_local_predichos
WHERE pr1.id_pronostico < 5000;
GO 2

-- E.4 Verificar que el caché de planes tiene datos
SELECT TOP 5
       CAST(qs.total_worker_time / 1000.0 AS DECIMAL(10,2)) AS cpu_total_ms,
       qs.execution_count                                   AS ejecuciones,
       SUBSTRING(st.text, 1, 80)                            AS inicio_consulta
FROM sys.dm_exec_query_stats qs
CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) st
ORDER BY qs.total_worker_time DESC;
GO
