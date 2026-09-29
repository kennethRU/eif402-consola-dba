/* =========================================================================
   04_functions.sql
   Funciones escalares y de tabla para el Sistema de Quinielas de Fútbol.
   Deben ejecutarse DESPUÉS de 03_views_procedures_triggers.sql.

   Funciones incluidas:
     1. fn_ObtenerResultadoPartido   (escalar)  -> 'L', 'V' o 'E'
     2. fn_EsInscripcionAbierta      (escalar)  -> 1 (abierta) / 0 (cerrada)
     3. fn_PartidosConPronostico     (TVF inline) -> partidos de un usuario/quiniela
   ========================================================================= */

USE QuinielasDB;
GO

IF OBJECT_ID('quiniela.fn_ObtenerResultadoPartido', 'FN') IS NOT NULL
    DROP FUNCTION quiniela.fn_ObtenerResultadoPartido;
GO
CREATE FUNCTION quiniela.fn_ObtenerResultadoPartido
(
    @id_partido INT
)
RETURNS CHAR(1)
AS
BEGIN
    DECLARE @resultado CHAR(1);

    SELECT @resultado =
        CASE
            WHEN goles_local  >  goles_visitante THEN 'L'
            WHEN goles_local  <  goles_visitante THEN 'V'
            ELSE                                      'E'
        END
    FROM  quiniela.Partido
    WHERE id_partido = @id_partido
      AND estado     = 'Finalizado'
      AND goles_local     IS NOT NULL
      AND goles_visitante IS NOT NULL;

    RETURN @resultado;
END
GO

IF OBJECT_ID('quiniela.fn_EsInscripcionAbierta', 'FN') IS NOT NULL
    DROP FUNCTION quiniela.fn_EsInscripcionAbierta;
GO
CREATE FUNCTION quiniela.fn_EsInscripcionAbierta
(
    @id_quiniela INT
)
RETURNS BIT
AS
BEGIN
    DECLARE @abierta BIT = 0;

    SELECT @abierta = 1
    FROM   quiniela.Quiniela
    WHERE  id_quiniela = @id_quiniela
      AND  estado      = 'Abierta'
      AND  GETDATE() BETWEEN fecha_inicio_inscripcion
                         AND fecha_cierre_inscripcion;

    RETURN @abierta;
END
GO

IF OBJECT_ID('quiniela.fn_PartidosConPronostico', 'IF') IS NOT NULL
    DROP FUNCTION quiniela.fn_PartidosConPronostico;
GO
CREATE FUNCTION quiniela.fn_PartidosConPronostico
(
    @id_usuario  INT,
    @id_quiniela INT
)
RETURNS TABLE
AS
RETURN
(
    SELECT
        p.id_partido,
        p.fecha_hora,
        p.estado                         AS estado_partido,
        el.nombre                        AS equipo_local,
        ev.nombre                        AS equipo_visitante,
        p.goles_local,
        p.goles_visitante,
        pr.id_pronostico,
        pr.goles_local_predichos,
        pr.goles_visitante_predichos,
        pr.puntos_obtenidos,
        CAST(
            CASE
                WHEN p.fecha_hora <= GETDATE()
                  OR p.estado     <> 'Pendiente'
                THEN 1 ELSE 0
            END
        AS BIT)                          AS bloqueado,
        quiniela.fn_ObtenerResultadoPartido(p.id_partido) AS resultado_real
    FROM  quiniela.Partido p
    JOIN  quiniela.Equipo  el ON el.id_equipo = p.id_equipo_local
    JOIN  quiniela.Equipo  ev ON ev.id_equipo = p.id_equipo_visitante
    LEFT  JOIN quiniela.Pronostico pr
               ON  pr.id_partido = p.id_partido
               AND pr.id_usuario = @id_usuario
    WHERE p.id_quiniela = @id_quiniela
);
GO

PRINT 'Funciones creadas: fn_ObtenerResultadoPartido, fn_EsInscripcionAbierta, fn_PartidosConPronostico';
GO
