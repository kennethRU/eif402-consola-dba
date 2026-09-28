/* =========================================================================
   03_views_procedures_triggers.sql
   Vistas para ranking, trigger de seguridad en pronósticos y
   procedimiento de cálculo automático de puntuaciones.
   ========================================================================= */

USE QuinielasDB;
GO

/* --------------------------------------------------------------
   VISTA 1: Ranking por quiniela
   -------------------------------------------------------------- */
IF OBJECT_ID('quiniela.vw_Ranking', 'V') IS NOT NULL
    DROP VIEW quiniela.vw_Ranking;
GO
CREATE VIEW quiniela.vw_Ranking AS
SELECT
    p.id_quiniela,
    q.nombre                                                AS quiniela,
    u.id_usuario,
    u.nombre_usuario,
    u.nombre_completo,
    p.puntaje_total,
    RANK() OVER (PARTITION BY p.id_quiniela
                 ORDER BY p.puntaje_total DESC)             AS posicion
FROM quiniela.Participacion p
INNER JOIN quiniela.Usuario  u ON u.id_usuario  = p.id_usuario
INNER JOIN quiniela.Quiniela q ON q.id_quiniela = p.id_quiniela;
GO

/* --------------------------------------------------------------
   VISTA 2: Historial de pronósticos con resultado real
   -------------------------------------------------------------- */
IF OBJECT_ID('quiniela.vw_HistorialPronosticos', 'V') IS NOT NULL
    DROP VIEW quiniela.vw_HistorialPronosticos;
GO
CREATE VIEW quiniela.vw_HistorialPronosticos AS
SELECT
    pr.id_pronostico,
    pr.id_usuario,
    u.nombre_usuario,
    pa.id_partido,
    pa.id_quiniela,
    el.nombre  AS equipo_local,
    ev.nombre  AS equipo_visitante,
    pa.fecha_hora,
    pa.estado  AS estado_partido,
    pr.goles_local_predichos,
    pr.goles_visitante_predichos,
    pa.goles_local,
    pa.goles_visitante,
    pr.puntos_obtenidos,
    pr.fecha_ingreso
FROM quiniela.Pronostico pr
INNER JOIN quiniela.Usuario u  ON u.id_usuario   = pr.id_usuario
INNER JOIN quiniela.Partido pa ON pa.id_partido  = pr.id_partido
INNER JOIN quiniela.Equipo  el ON el.id_equipo   = pa.id_equipo_local
INNER JOIN quiniela.Equipo  ev ON ev.id_equipo   = pa.id_equipo_visitante;
GO


/* --------------------------------------------------------------
   TRIGGER: Bloquea pronósticos si el partido ya inició
   Requisito 3.2: "Los pronósticos no puedan modificarse
   después de iniciado el partido."
   -------------------------------------------------------------- */
IF OBJECT_ID('quiniela.tr_Pronostico_BloquearPartidoIniciado', 'TR') IS NOT NULL
    DROP TRIGGER quiniela.tr_Pronostico_BloquearPartidoIniciado;
GO
CREATE TRIGGER quiniela.tr_Pronostico_BloquearPartidoIniciado
ON quiniela.Pronostico
INSTEAD OF INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    -- Detectar intentos sobre partidos ya iniciados o finalizados
    IF EXISTS (
        SELECT 1
        FROM inserted i
        INNER JOIN quiniela.Partido p ON p.id_partido = i.id_partido
        WHERE p.fecha_hora <= GETDATE()
           OR p.estado IN ('EnCurso','Finalizado')
    )
    BEGIN
        RAISERROR('No se permite ingresar o modificar pronósticos después de iniciado el partido.', 16, 1);
        RETURN;
    END

    -- Si es INSERT
    IF NOT EXISTS (SELECT 1 FROM deleted)
    BEGIN
        INSERT INTO quiniela.Pronostico
            (id_usuario, id_partido, goles_local_predichos,
             goles_visitante_predichos, fecha_ingreso, puntos_obtenidos)
        SELECT
            id_usuario, id_partido, goles_local_predichos,
            goles_visitante_predichos, ISNULL(fecha_ingreso, GETDATE()), 0
        FROM inserted;
    END
    ELSE
    BEGIN
        -- UPDATE
        UPDATE pr
           SET pr.goles_local_predichos      = i.goles_local_predichos,
               pr.goles_visitante_predichos  = i.goles_visitante_predichos,
               pr.fecha_ingreso              = GETDATE()
        FROM quiniela.Pronostico pr
        INNER JOIN inserted i ON i.id_pronostico = pr.id_pronostico;
    END
END
GO


/* --------------------------------------------------------------
   SP: Cálculo de puntuaciones de un partido finalizado
   Aplica la regla de puntuación configurada en la quiniela.
   -------------------------------------------------------------- */
IF OBJECT_ID('quiniela.sp_CalcularPuntosPartido', 'P') IS NOT NULL
    DROP PROCEDURE quiniela.sp_CalcularPuntosPartido;
GO
CREATE PROCEDURE quiniela.sp_CalcularPuntosPartido
    @id_partido INT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @goles_local        INT,
            @goles_visitante    INT,
            @id_quiniela        INT,
            @estado             VARCHAR(20),
            @pts_ganador        INT,
            @pts_exacto         INT,
            @pts_diferencia     INT;

    SELECT  @goles_local       = p.goles_local,
            @goles_visitante   = p.goles_visitante,
            @id_quiniela       = p.id_quiniela,
            @estado            = p.estado,
            @pts_ganador       = tp.puntos_acierto_ganador,
            @pts_exacto        = tp.puntos_marcador_exacto,
            @pts_diferencia    = tp.puntos_diferencia_goles
    FROM quiniela.Partido p
    INNER JOIN quiniela.Quiniela       q  ON q.id_quiniela        = p.id_quiniela
    INNER JOIN quiniela.TipoPuntuacion tp ON tp.id_tipo_puntuacion = q.id_tipo_puntuacion
    WHERE p.id_partido = @id_partido;

    IF @estado <> 'Finalizado' OR @goles_local IS NULL OR @goles_visitante IS NULL
    BEGIN
        RAISERROR('El partido no está finalizado o carece de resultado registrado.', 16, 1);
        RETURN;
    END

    -- Ganador real: L (local), V (visitante) o E (empate)
-- DESPUÉS
DECLARE @resultado_real CHAR(1) =
    quiniela.fn_ObtenerResultadoPartido(@id_partido);

    /* Regla implementada:
       - Marcador exacto:          puntos_marcador_exacto
       - Acierto ganador (o empate): puntos_acierto_ganador
       - Acierto diferencia de goles: puntos_diferencia_goles
       Los puntos NO son acumulativos: se toma el mayor aplicable.           */

    UPDATE pr
    SET pr.puntos_obtenidos =
        CASE
            WHEN pr.goles_local_predichos = @goles_local
             AND pr.goles_visitante_predichos = @goles_visitante
                THEN @pts_exacto
            WHEN (CASE WHEN pr.goles_local_predichos > pr.goles_visitante_predichos THEN 'L'
                       WHEN pr.goles_local_predichos < pr.goles_visitante_predichos THEN 'V'
                       ELSE 'E' END) = @resultado_real
                THEN @pts_ganador
                   + CASE WHEN (pr.goles_local_predichos - pr.goles_visitante_predichos)
                             = (@goles_local - @goles_visitante)
                          THEN @pts_diferencia ELSE 0 END
            ELSE 0
        END
    FROM quiniela.Pronostico pr
    WHERE pr.id_partido = @id_partido;

    -- Recalcular puntaje total de cada participante de la quiniela
    UPDATE part
    SET part.puntaje_total = ISNULL(s.total, 0)
    FROM quiniela.Participacion part
    OUTER APPLY (
        SELECT SUM(pr.puntos_obtenidos) AS total
        FROM quiniela.Pronostico pr
        INNER JOIN quiniela.Partido p ON p.id_partido = pr.id_partido
        WHERE pr.id_usuario = part.id_usuario
          AND p.id_quiniela = part.id_quiniela
    ) s
    WHERE part.id_quiniela = @id_quiniela;
END
GO


/* --------------------------------------------------------------
   SP: Registrar resultado oficial y recalcular puntajes
   -------------------------------------------------------------- */
IF OBJECT_ID('quiniela.sp_RegistrarResultado', 'P') IS NOT NULL
    DROP PROCEDURE quiniela.sp_RegistrarResultado;
GO
CREATE PROCEDURE quiniela.sp_RegistrarResultado
    @id_partido       INT,
    @goles_local      INT,
    @goles_visitante  INT
AS
BEGIN
    SET NOCOUNT ON;

    IF @goles_local < 0 OR @goles_visitante < 0
    BEGIN
        RAISERROR('Los goles no pueden ser negativos.', 16, 1);
        RETURN;
    END

    UPDATE quiniela.Partido
       SET goles_local              = @goles_local,
           goles_visitante          = @goles_visitante,
           estado                   = 'Finalizado',
           fecha_registro_resultado = GETDATE()
     WHERE id_partido = @id_partido;

    EXEC quiniela.sp_CalcularPuntosPartido @id_partido;
END
GO

PRINT 'Vistas, trigger y procedimientos creados correctamente.';
GO
