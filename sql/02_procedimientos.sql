/* =====================================================================
   EIF402 - Proyecto Integrador
   Script 02: Procedimientos almacenados de la herramienta

   Toda operación que MODIFICA el estado del SGBD se ejecuta a través de
   estos procedimientos. La aplicación nunca envía SQL dinámico arbitrario:
   solo invoca procedimientos con parámetros validados. Esto evita
   inyección SQL y deja traza en dba.bitacora_mantenimiento.
   ===================================================================== */

/* ---------------------------------------------------------------------
   dba.usp_registrar_snapshot_almacenamiento
   Captura el tamaño actual de cada archivo de datos y log.
   Se invoca desde el módulo 3 o desde un Job programado.
   --------------------------------------------------------------------- */
CREATE OR ALTER PROCEDURE dba.usp_registrar_snapshot_almacenamiento
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO dba.snapshot_almacenamiento
        (nombre_base_datos, nombre_archivo, tipo_archivo, nombre_filegroup,
         tamano_mb, usado_mb, libre_mb)
    SELECT
        DB_NAME(),
        df.name,
        df.type_desc,
        fg.name,
        CAST(df.size * 8.0 / 1024 AS DECIMAL(18,2)),
        CAST(CAST(FILEPROPERTY(df.name, 'SpaceUsed') AS BIGINT) * 8.0 / 1024 AS DECIMAL(18,2)),
        CAST((df.size - CAST(FILEPROPERTY(df.name, 'SpaceUsed') AS BIGINT)) * 8.0 / 1024 AS DECIMAL(18,2))
    FROM sys.database_files df
    LEFT JOIN sys.filegroups fg ON fg.data_space_id = df.data_space_id;

    SELECT @@ROWCOUNT AS archivos_registrados, SYSDATETIME() AS fecha_captura;
END;
GO

/* ---------------------------------------------------------------------
   dba.usp_actualizar_estadisticas
   Recalcula estadísticas. Si no se indica tabla, actualiza toda la BD.
   @muestreo_completo = 1  -> WITH FULLSCAN (más preciso, más costoso)
   --------------------------------------------------------------------- */
CREATE OR ALTER PROCEDURE dba.usp_actualizar_estadisticas
    @nombre_esquema     SYSNAME = NULL,
    @nombre_tabla       SYSNAME = NULL,
    @muestreo_completo  BIT     = 0
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @comando       NVARCHAR(MAX),
            @inicio        DATETIME2(3) = SYSDATETIME(),
            @objeto        NVARCHAR(400),
            @mensaje       NVARCHAR(MAX) = NULL,
            @resultado     NVARCHAR(20)  = 'EXITOSO',
            @numero_error  INT           = 0;

    IF @nombre_tabla IS NOT NULL
    BEGIN
        SET @nombre_esquema = ISNULL(@nombre_esquema, N'dbo');
        SET @objeto = QUOTENAME(@nombre_esquema) + N'.' + QUOTENAME(@nombre_tabla);

        IF OBJECT_ID(@objeto, 'U') IS NULL
        BEGIN
            RAISERROR(N'La tabla %s no existe en la base de datos actual.', 16, 1, @objeto);
            RETURN;
        END

        SET @comando = N'UPDATE STATISTICS ' + @objeto
                     + CASE WHEN @muestreo_completo = 1 THEN N' WITH FULLSCAN;' ELSE N';' END;
    END
    ELSE
    BEGIN
        SET @objeto  = N'(base de datos completa: ' + DB_NAME() + N')';
        SET @comando = CASE WHEN @muestreo_completo = 1
                            THEN N'EXEC sp_updatestats;'
                            ELSE N'EXEC sp_updatestats;' END;
    END

    BEGIN TRY
        EXEC sys.sp_executesql @comando;
        SET @mensaje = N'Estadísticas actualizadas correctamente.';
    END TRY
    BEGIN CATCH
        SET @resultado    = N'FALLIDO';
        SET @numero_error = ERROR_NUMBER();
        SET @mensaje      = CONCAT(N'Error ', ERROR_NUMBER(), N': ', ERROR_MESSAGE());
    END CATCH

    INSERT INTO dba.bitacora_mantenimiento
        (tipo_operacion, objeto_afectado, comando_ejecutado, resultado, mensaje, duracion_ms)
    VALUES
        (N'ESTADISTICAS', @objeto, @comando, @resultado, @mensaje,
         DATEDIFF(MILLISECOND, @inicio, SYSDATETIME()));

    SELECT @resultado    AS resultado,
           @objeto       AS objeto_afectado,
           @comando      AS comando_ejecutado,
           @mensaje      AS mensaje,
           @numero_error AS numero_error,
           DATEDIFF(MILLISECOND, @inicio, SYSDATETIME()) AS duracion_ms;
END;
GO

/* ---------------------------------------------------------------------
   dba.usp_validar_modulos
   Equivalente en SQL Server a detectar "objetos inválidos" de Oracle.

   SQL Server no marca los objetos como inválidos: un procedimiento o
   vista cuya tabla base fue eliminada o renombrada sigue existiendo en
   sys.objects y solo falla al ejecutarse (deferred name resolution).
   Para detectarlos anticipadamente se intenta refrescar cada módulo con
   sp_refreshsqlmodule dentro de un TRY...CATCH: si falla, el objeto está
   roto. Se combina con sys.sql_expression_dependencies para encontrar
   referencias a entidades que ya no existen.
   --------------------------------------------------------------------- */
CREATE OR ALTER PROCEDURE dba.usp_validar_modulos
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @inicio DATETIME2(3) = SYSDATETIME();

    -- Se marcan como corregidos los registros previos; se vuelven a detectar.
    UPDATE dba.objeto_invalido
       SET corregido = 1, fecha_correccion = SYSDATETIME()
     WHERE corregido = 0;

    DECLARE @esquema SYSNAME, @objeto SYSNAME, @tipo NVARCHAR(60), @nombre NVARCHAR(400);

    DECLARE cur_modulos CURSOR LOCAL FAST_FORWARD FOR
        SELECT SCHEMA_NAME(o.schema_id), o.name, o.type_desc
        FROM sys.sql_modules m
        JOIN sys.objects o ON o.object_id = m.object_id
        WHERE o.is_ms_shipped = 0
          AND o.type IN ('P', 'V', 'FN', 'IF', 'TF', 'TR')
          AND SCHEMA_NAME(o.schema_id) <> 'dba';

    OPEN cur_modulos;
    FETCH NEXT FROM cur_modulos INTO @esquema, @objeto, @tipo;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @nombre = QUOTENAME(@esquema) + N'.' + QUOTENAME(@objeto);

        BEGIN TRY
            EXEC sys.sp_refreshsqlmodule @name = @nombre;
        END TRY
        BEGIN CATCH
            INSERT INTO dba.objeto_invalido
                (nombre_esquema, nombre_objeto, tipo_objeto, numero_error, mensaje_error)
            VALUES
                (@esquema, @objeto, @tipo, ERROR_NUMBER(), ERROR_MESSAGE());
        END CATCH

        FETCH NEXT FROM cur_modulos INTO @esquema, @objeto, @tipo;
    END

    CLOSE cur_modulos;
    DEALLOCATE cur_modulos;

    INSERT INTO dba.bitacora_mantenimiento
        (tipo_operacion, objeto_afectado, comando_ejecutado, resultado, mensaje, duracion_ms)
    SELECT N'VALIDACION',
           N'(todos los módulos SQL)',
           N'EXEC dba.usp_validar_modulos',
           N'EXITOSO',
           CONCAT(N'Objetos con problemas detectados: ',
                  (SELECT COUNT(*) FROM dba.objeto_invalido WHERE corregido = 0)),
           DATEDIFF(MILLISECOND, @inicio, SYSDATETIME());

    SELECT nombre_esquema, nombre_objeto, tipo_objeto, numero_error, mensaje_error, fecha_deteccion
    FROM dba.objeto_invalido
    WHERE corregido = 0
    ORDER BY nombre_esquema, nombre_objeto;
END;
GO

/* ---------------------------------------------------------------------
   dba.usp_recompilar_objeto
   Recompila (refresca) un módulo específico detectado como inválido.
   Si @nombre_objeto es NULL, intenta recompilar todos los pendientes.
   --------------------------------------------------------------------- */
CREATE OR ALTER PROCEDURE dba.usp_recompilar_objeto
    @nombre_esquema SYSNAME = NULL,
    @nombre_objeto  SYSNAME = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @resultados TABLE
    (
        nombre_esquema SYSNAME,
        nombre_objeto  SYSNAME,
        resultado      NVARCHAR(20),
        mensaje        NVARCHAR(MAX)
    );

    DECLARE @esquema SYSNAME, @objeto SYSNAME, @nombre NVARCHAR(400),
            @inicio  DATETIME2(3) = SYSDATETIME();

    DECLARE cur_pendientes CURSOR LOCAL FAST_FORWARD FOR
        SELECT nombre_esquema, nombre_objeto
        FROM dba.objeto_invalido
        WHERE corregido = 0
          AND (@nombre_objeto IS NULL
               OR (nombre_objeto = @nombre_objeto
                   AND nombre_esquema = ISNULL(@nombre_esquema, nombre_esquema)));

    OPEN cur_pendientes;
    FETCH NEXT FROM cur_pendientes INTO @esquema, @objeto;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @nombre = QUOTENAME(@esquema) + N'.' + QUOTENAME(@objeto);

        BEGIN TRY
            EXEC sys.sp_refreshsqlmodule @name = @nombre;

            UPDATE dba.objeto_invalido
               SET corregido = 1, fecha_correccion = SYSDATETIME()
             WHERE nombre_esquema = @esquema AND nombre_objeto = @objeto AND corregido = 0;

            INSERT INTO @resultados VALUES (@esquema, @objeto, N'EXITOSO', N'Objeto recompilado correctamente.');
        END TRY
        BEGIN CATCH
            INSERT INTO @resultados VALUES (@esquema, @objeto, N'FALLIDO',
                CONCAT(N'Error ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
        END CATCH

        FETCH NEXT FROM cur_pendientes INTO @esquema, @objeto;
    END

    CLOSE cur_pendientes;
    DEALLOCATE cur_pendientes;

    INSERT INTO dba.bitacora_mantenimiento
        (tipo_operacion, objeto_afectado, comando_ejecutado, resultado, mensaje, duracion_ms)
    SELECT N'RECOMPILACION',
           ISNULL(QUOTENAME(@nombre_esquema) + N'.' + QUOTENAME(@nombre_objeto), N'(todos los pendientes)'),
           N'EXEC sys.sp_refreshsqlmodule',
           CASE WHEN EXISTS (SELECT 1 FROM @resultados WHERE resultado = N'FALLIDO')
                THEN N'FALLIDO' ELSE N'EXITOSO' END,
           CONCAT(N'Recompilados: ', (SELECT COUNT(*) FROM @resultados WHERE resultado = N'EXITOSO'),
                  N' / Fallidos: ',  (SELECT COUNT(*) FROM @resultados WHERE resultado = N'FALLIDO')),
           DATEDIFF(MILLISECOND, @inicio, SYSDATETIME());

    SELECT * FROM @resultados;
END;
GO
