/* =====================================================================
   EIF402 - Administración de Bases de Datos
   Proyecto: Herramienta de monitoreo, administración y auditoría
   Script 01: Esquema de apoyo "dba"

   Este esquema NO reemplaza la información del SGBD. Solo agrega:
     1. Una bitácora histórica de tamaño (SQL Server no conserva
        histórico de crecimiento; hay que registrarlo).
     2. Una bitácora de operaciones de mantenimiento ejecutadas
        desde la herramienta (trazabilidad y confirmación al usuario).
     3. Una tabla de resultados del validador de módulos SQL
        (equivalente en SQL Server a los "objetos inválidos" de Oracle).

   Ejecutar sobre la base de datos que se va a monitorear.
   ===================================================================== */

IF SCHEMA_ID('dba') IS NULL
    EXEC('CREATE SCHEMA dba AUTHORIZATION dbo;');
GO

/* ---------------------------------------------------------------------
   1. Histórico de almacenamiento
   --------------------------------------------------------------------- */
IF OBJECT_ID('dba.snapshot_almacenamiento', 'U') IS NULL
BEGIN
    CREATE TABLE dba.snapshot_almacenamiento
    (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        fecha_captura       DATETIME2(0)  NOT NULL CONSTRAINT df_snap_fecha DEFAULT SYSDATETIME(),
        nombre_base_datos   SYSNAME       NOT NULL,
        nombre_archivo      SYSNAME       NOT NULL,
        tipo_archivo        NVARCHAR(60)  NOT NULL,   -- ROWS / LOG
        nombre_filegroup    SYSNAME       NULL,
        tamano_mb           DECIMAL(18,2) NOT NULL,
        usado_mb            DECIMAL(18,2) NOT NULL,
        libre_mb            DECIMAL(18,2) NOT NULL,
        CONSTRAINT pk_snapshot_almacenamiento PRIMARY KEY CLUSTERED (id)
    );

    CREATE INDEX ix_snapshot_fecha
        ON dba.snapshot_almacenamiento (nombre_base_datos, fecha_captura);
END
GO

/* ---------------------------------------------------------------------
   2. Bitácora de mantenimiento preventivo
   --------------------------------------------------------------------- */
IF OBJECT_ID('dba.bitacora_mantenimiento', 'U') IS NULL
BEGIN
    CREATE TABLE dba.bitacora_mantenimiento
    (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        fecha_ejecucion     DATETIME2(0)   NOT NULL CONSTRAINT df_bitacora_fecha DEFAULT SYSDATETIME(),
        tipo_operacion      NVARCHAR(60)   NOT NULL,  -- ESTADISTICAS / RECOMPILACION / VALIDACION
        objeto_afectado     NVARCHAR(400)  NULL,
        comando_ejecutado   NVARCHAR(MAX)  NULL,
        resultado           NVARCHAR(20)   NOT NULL,  -- EXITOSO / FALLIDO
        mensaje             NVARCHAR(MAX)  NULL,
        duracion_ms         INT            NULL,
        usuario_solicitante SYSNAME        NOT NULL CONSTRAINT df_bitacora_usr DEFAULT SUSER_SNAME(),
        CONSTRAINT pk_bitacora_mantenimiento PRIMARY KEY CLUSTERED (id)
    );

    CREATE INDEX ix_bitacora_fecha
        ON dba.bitacora_mantenimiento (fecha_ejecucion DESC);
END
GO

/* ---------------------------------------------------------------------
   3. Resultado del validador de módulos (objetos inválidos)
   --------------------------------------------------------------------- */
IF OBJECT_ID('dba.objeto_invalido', 'U') IS NULL
BEGIN
    CREATE TABLE dba.objeto_invalido
    (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        fecha_deteccion     DATETIME2(0)  NOT NULL CONSTRAINT df_obj_fecha DEFAULT SYSDATETIME(),
        nombre_esquema      SYSNAME       NOT NULL,
        nombre_objeto       SYSNAME       NOT NULL,
        tipo_objeto         NVARCHAR(60)  NOT NULL,
        numero_error        INT           NULL,
        mensaje_error       NVARCHAR(MAX) NULL,
        corregido           BIT           NOT NULL CONSTRAINT df_obj_corregido DEFAULT 0,
        fecha_correccion    DATETIME2(0)  NULL,
        CONSTRAINT pk_objeto_invalido PRIMARY KEY CLUSTERED (id)
    );

    CREATE UNIQUE INDEX ux_objeto_invalido_vigente
        ON dba.objeto_invalido (nombre_esquema, nombre_objeto)
        WHERE corregido = 0;
END
GO
