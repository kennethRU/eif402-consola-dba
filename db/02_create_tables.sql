/* =========================================================================
   02_create_tables.sql
   Creación de tablas, claves primarias, foráneas y restricciones (CHECK/UNIQUE).
   Orden de creación respeta dependencias entre entidades.
   ========================================================================= */

USE QuinielasDB;
GO

/* --------------------------------------------------------------
   1. ROL  (catálogo: Administrador, Jugador)
   -------------------------------------------------------------- */
CREATE TABLE quiniela.Rol (
    id_rol       INT           IDENTITY(1,1) NOT NULL,
    nombre       VARCHAR(20)   NOT NULL,
    descripcion  VARCHAR(200)  NULL,
    CONSTRAINT PK_Rol        PRIMARY KEY (id_rol),
    CONSTRAINT UQ_Rol_Nombre UNIQUE      (nombre),
    CONSTRAINT CK_Rol_Nombre CHECK (nombre IN ('Administrador','Jugador'))
);
GO

/* --------------------------------------------------------------
   2. USUARIO
   -------------------------------------------------------------- */
CREATE TABLE quiniela.Usuario (
    id_usuario        INT           IDENTITY(1,1) NOT NULL,
    nombre_completo   VARCHAR(100)  NOT NULL,
    correo            VARCHAR(100)  NOT NULL,
    nombre_usuario    VARCHAR(50)   NOT NULL,
    contrasena_hash   VARCHAR(255)  NOT NULL,
    fecha_nacimiento  DATE          NOT NULL,
    id_rol            INT           NOT NULL,
    fecha_registro    DATETIME      NOT NULL CONSTRAINT DF_Usuario_FechaReg DEFAULT (GETDATE()),
    activo            BIT           NOT NULL CONSTRAINT DF_Usuario_Activo   DEFAULT (1),
    CONSTRAINT PK_Usuario          PRIMARY KEY (id_usuario),
    CONSTRAINT UQ_Usuario_Correo   UNIQUE (correo),
    CONSTRAINT UQ_Usuario_Username UNIQUE (nombre_usuario),
    CONSTRAINT FK_Usuario_Rol      FOREIGN KEY (id_rol) REFERENCES quiniela.Rol(id_rol),
    CONSTRAINT CK_Usuario_Correo   CHECK (correo LIKE '%_@_%._%'),
    CONSTRAINT CK_Usuario_FechaNac CHECK (fecha_nacimiento < CAST(GETDATE() AS DATE))
);
GO

/* --------------------------------------------------------------
   3. TIPO_PUNTUACION
   Catálogo con diferentes métodos de puntuación.
   Solo uno se programa en el cálculo (simplificación del alcance).
   -------------------------------------------------------------- */
CREATE TABLE quiniela.TipoPuntuacion (
    id_tipo_puntuacion        INT           IDENTITY(1,1) NOT NULL,
    nombre                    VARCHAR(50)   NOT NULL,
    descripcion               VARCHAR(500)  NULL,
    puntos_acierto_ganador    INT           NOT NULL CONSTRAINT DF_TP_Ganador  DEFAULT (1),
    puntos_marcador_exacto    INT           NOT NULL CONSTRAINT DF_TP_Exacto   DEFAULT (3),
    puntos_diferencia_goles   INT           NOT NULL CONSTRAINT DF_TP_Difer    DEFAULT (0),
    activo                    BIT           NOT NULL CONSTRAINT DF_TP_Activo   DEFAULT (1),
    CONSTRAINT PK_TipoPuntuacion       PRIMARY KEY (id_tipo_puntuacion),
    CONSTRAINT UQ_TipoPuntuacion_Nom   UNIQUE (nombre),
    CONSTRAINT CK_TP_Puntos_NoNeg      CHECK (puntos_acierto_ganador  >= 0
                                          AND puntos_marcador_exacto  >= 0
                                          AND puntos_diferencia_goles >= 0)
);
GO

/* --------------------------------------------------------------
   4. QUINIELA
   -------------------------------------------------------------- */
CREATE TABLE quiniela.Quiniela (
    id_quiniela                 INT            IDENTITY(1,1) NOT NULL,
    nombre                      VARCHAR(100)   NOT NULL,
    descripcion                 VARCHAR(500)   NULL,
    reglas                      VARCHAR(MAX)   NULL,
    fecha_inicio_inscripcion    DATETIME       NOT NULL,
    fecha_cierre_inscripcion    DATETIME       NOT NULL,
    estado                      VARCHAR(20)    NOT NULL CONSTRAINT DF_Quin_Estado   DEFAULT ('Abierta'),
    modalidad                   VARCHAR(10)    NOT NULL CONSTRAINT DF_Quin_Modal    DEFAULT ('Publica'),
    id_tipo_puntuacion          INT            NOT NULL,
    id_admin_creador            INT            NOT NULL,
    costo_inscripcion           DECIMAL(10,2)  NOT NULL CONSTRAINT DF_Quin_Costo    DEFAULT (0),
    premio                      DECIMAL(10,2)  NOT NULL CONSTRAINT DF_Quin_Premio   DEFAULT (0),
    fecha_creacion              DATETIME       NOT NULL CONSTRAINT DF_Quin_FechaCr  DEFAULT (GETDATE()),
    CONSTRAINT PK_Quiniela                  PRIMARY KEY (id_quiniela),
    CONSTRAINT UQ_Quiniela_Nombre           UNIQUE (nombre),
    CONSTRAINT FK_Quiniela_TipoPuntuacion   FOREIGN KEY (id_tipo_puntuacion) REFERENCES quiniela.TipoPuntuacion(id_tipo_puntuacion),
    CONSTRAINT FK_Quiniela_Admin            FOREIGN KEY (id_admin_creador)   REFERENCES quiniela.Usuario(id_usuario),
    CONSTRAINT CK_Quiniela_Estado           CHECK (estado    IN ('Abierta','Cerrada','Finalizada')),
    CONSTRAINT CK_Quiniela_Modalidad        CHECK (modalidad IN ('Publica','Privada')),
    CONSTRAINT CK_Quiniela_Fechas           CHECK (fecha_cierre_inscripcion > fecha_inicio_inscripcion),
    CONSTRAINT CK_Quiniela_Costo            CHECK (costo_inscripcion >= 0 AND premio >= 0)
);
GO

/* --------------------------------------------------------------
   5. PARTICIPACION  (tabla de relación Usuario <-> Quiniela)
   -------------------------------------------------------------- */
CREATE TABLE quiniela.Participacion (
    id_participacion    INT       IDENTITY(1,1) NOT NULL,
    id_usuario          INT       NOT NULL,
    id_quiniela         INT       NOT NULL,
    fecha_inscripcion   DATETIME  NOT NULL CONSTRAINT DF_Part_FechaIns DEFAULT (GETDATE()),
    reglas_aceptadas    BIT       NOT NULL CONSTRAINT DF_Part_Reglas   DEFAULT (0),
    estado_pago         VARCHAR(20) NOT NULL CONSTRAINT DF_Part_Pago   DEFAULT ('N/A'),
    puntaje_total       INT       NOT NULL CONSTRAINT DF_Part_Puntaje  DEFAULT (0),
    CONSTRAINT PK_Participacion             PRIMARY KEY (id_participacion),
    CONSTRAINT UQ_Participacion_UsuQui      UNIQUE (id_usuario, id_quiniela),
    CONSTRAINT FK_Participacion_Usuario     FOREIGN KEY (id_usuario)  REFERENCES quiniela.Usuario(id_usuario),
    CONSTRAINT FK_Participacion_Quiniela    FOREIGN KEY (id_quiniela) REFERENCES quiniela.Quiniela(id_quiniela),
    CONSTRAINT CK_Participacion_EstadoPago  CHECK (estado_pago IN ('Pendiente','Pagado','N/A')),
    CONSTRAINT CK_Participacion_Puntaje     CHECK (puntaje_total >= 0)
);
GO

/* --------------------------------------------------------------
   6. EQUIPO
   -------------------------------------------------------------- */
CREATE TABLE quiniela.Equipo (
    id_equipo   INT           IDENTITY(1,1) NOT NULL,
    nombre      VARCHAR(100)  NOT NULL,
    pais        VARCHAR(50)   NULL,
    ciudad      VARCHAR(50)   NULL,
    escudo_url  VARCHAR(255)  NULL,
    CONSTRAINT PK_Equipo        PRIMARY KEY (id_equipo),
    CONSTRAINT UQ_Equipo_Nombre UNIQUE (nombre)
);
GO

/* --------------------------------------------------------------
   7. PARTIDO
   -------------------------------------------------------------- */
CREATE TABLE quiniela.Partido (
    id_partido                 INT        IDENTITY(1,1) NOT NULL,
    id_quiniela                INT        NOT NULL,
    id_equipo_local            INT        NOT NULL,
    id_equipo_visitante        INT        NOT NULL,
    fecha_hora                 DATETIME   NOT NULL,
    estado                     VARCHAR(20) NOT NULL CONSTRAINT DF_Part_Estado DEFAULT ('Pendiente'),
    goles_local                INT        NULL,
    goles_visitante            INT        NULL,
    fecha_registro_resultado   DATETIME   NULL,
    CONSTRAINT PK_Partido                     PRIMARY KEY (id_partido),
    CONSTRAINT FK_Partido_Quiniela            FOREIGN KEY (id_quiniela)         REFERENCES quiniela.Quiniela(id_quiniela),
    CONSTRAINT FK_Partido_EquipoLocal         FOREIGN KEY (id_equipo_local)     REFERENCES quiniela.Equipo(id_equipo),
    CONSTRAINT FK_Partido_EquipoVisitante     FOREIGN KEY (id_equipo_visitante) REFERENCES quiniela.Equipo(id_equipo),
    CONSTRAINT CK_Partido_EquiposDistintos    CHECK (id_equipo_local <> id_equipo_visitante),
    CONSTRAINT CK_Partido_Estado              CHECK (estado IN ('Pendiente','EnCurso','Finalizado')),
    CONSTRAINT CK_Partido_Goles               CHECK (
        (goles_local IS NULL AND goles_visitante IS NULL)
        OR (goles_local >= 0 AND goles_visitante >= 0)
    ),
    CONSTRAINT CK_Partido_ResultadoFinalizado CHECK (
        (estado <> 'Finalizado')
        OR (goles_local IS NOT NULL AND goles_visitante IS NOT NULL)
    )
);
GO

/* --------------------------------------------------------------
   8. PRONOSTICO
   -------------------------------------------------------------- */
CREATE TABLE quiniela.Pronostico (
    id_pronostico                INT       IDENTITY(1,1) NOT NULL,
    id_usuario                   INT       NOT NULL,
    id_partido                   INT       NOT NULL,
    goles_local_predichos        INT       NOT NULL,
    goles_visitante_predichos    INT       NOT NULL,
    fecha_ingreso                DATETIME  NOT NULL CONSTRAINT DF_Pron_FechaIng DEFAULT (GETDATE()),
    puntos_obtenidos             INT       NOT NULL CONSTRAINT DF_Pron_Puntos   DEFAULT (0),
    CONSTRAINT PK_Pronostico              PRIMARY KEY (id_pronostico),
    CONSTRAINT UQ_Pronostico_UsuPar       UNIQUE (id_usuario, id_partido),
    CONSTRAINT FK_Pronostico_Usuario      FOREIGN KEY (id_usuario) REFERENCES quiniela.Usuario(id_usuario),
    CONSTRAINT FK_Pronostico_Partido      FOREIGN KEY (id_partido) REFERENCES quiniela.Partido(id_partido),
    CONSTRAINT CK_Pronostico_Goles        CHECK (goles_local_predichos >= 0 AND goles_visitante_predichos >= 0),
    CONSTRAINT CK_Pronostico_Puntos       CHECK (puntos_obtenidos >= 0)
);
GO

/* --------------------------------------------------------------
   ÍNDICES ADICIONALES para consultas frecuentes
   -------------------------------------------------------------- */
CREATE INDEX IX_Usuario_Rol            ON quiniela.Usuario(id_rol);
CREATE INDEX IX_Quiniela_Estado        ON quiniela.Quiniela(estado);
CREATE INDEX IX_Partido_Quiniela       ON quiniela.Partido(id_quiniela);
CREATE INDEX IX_Partido_Estado         ON quiniela.Partido(estado);
CREATE INDEX IX_Partido_Fecha          ON quiniela.Partido(fecha_hora);
CREATE INDEX IX_Pronostico_Partido     ON quiniela.Pronostico(id_partido);
CREATE INDEX IX_Participacion_Quiniela ON quiniela.Participacion(id_quiniela);
GO

PRINT 'Tablas, claves e índices creados correctamente.';
GO
