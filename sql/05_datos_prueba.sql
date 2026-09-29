/* =====================================================================
   EIF402 - Proyecto Integrador
   Script 05: Generación de datos de prueba para QuinielasDB

   Propósito: llevar la base de ~39 filas a varios cientos de miles, para
   que los módulos de rendimiento, almacenamiento y mantenimiento tengan
   métricas reales que mostrar.

   Respeta:
     - El orden de las llaves foráneas
     - Las 16 restricciones CHECK del esquema
     - Los valores IDENTITY (no se insertan explícitamente)

   El trigger tr_Pronostico_BloquearPartidoIniciado se desactiva durante
   la carga y se reactiva al final: bloquea pronósticos sobre partidos ya
   iniciados, y aquí se generan datos históricos a propósito.

   ADVERTENCIA: la base está en modelo FULL sin respaldos de log, así que
   el archivo de transacciones va a crecer bastante durante la carga.
   Eso es intencional: alimenta los módulos 3 y 4. Si el disco es
   limitado, reduzca los parámetros de la sección 1.

   Tiempo estimado: 1 a 3 minutos.
   ===================================================================== */

USE QuinielasDB;
GO

SET NOCOUNT ON;
GO

/* ---------------------------------------------------------------------
   1. Parámetros de volumen
   --------------------------------------------------------------------- */
DECLARE @equipos_nuevos            INT = 40;
DECLARE @usuarios_nuevos           INT = 2000;
DECLARE @quinielas_nuevas          INT = 30;
DECLARE @partidos_por_quiniela     INT = 90;
DECLARE @participantes_por_quiniela INT = 150;
-- Pronósticos resultantes ≈ quinielas × partidos × participantes / 2

DECLARE @inicio DATETIME2 = SYSDATETIME();

/* ---------------------------------------------------------------------
   2. Tabla auxiliar de números
   --------------------------------------------------------------------- */
IF OBJECT_ID('tempdb..#numeros') IS NOT NULL DROP TABLE #numeros;

CREATE TABLE #numeros (n INT NOT NULL PRIMARY KEY CLUSTERED);

INSERT INTO #numeros (n)
SELECT TOP (100000) ROW_NUMBER() OVER (ORDER BY (SELECT NULL))
FROM sys.all_objects a CROSS JOIN sys.all_objects b;

/* ---------------------------------------------------------------------
   3. Referencias a datos existentes
   --------------------------------------------------------------------- */
DECLARE @rol_jugador INT = (SELECT TOP 1 id_rol FROM quiniela.Rol WHERE nombre = 'Jugador');
DECLARE @rol_admin   INT = (SELECT TOP 1 id_rol FROM quiniela.Rol WHERE nombre = 'Administrador');

IF @rol_jugador IS NULL OR @rol_admin IS NULL
BEGIN
    RAISERROR('No se encontraron los roles Jugador y Administrador en quiniela.Rol.', 16, 1);
    RETURN;
END

DECLARE @id_admin INT =
    (SELECT TOP 1 id_usuario FROM quiniela.Usuario WHERE id_rol = @rol_admin ORDER BY id_usuario);

IF @id_admin IS NULL
BEGIN
    INSERT INTO quiniela.Usuario
        (nombre_completo, correo, nombre_usuario, contrasena_hash,
         fecha_nacimiento, id_rol, fecha_registro, activo)
    VALUES
        ('Administrador de Pruebas', 'admin.pruebas@quinielas.test', 'admin_pruebas',
         CONVERT(VARCHAR(255), HASHBYTES('SHA2_256', 'clave_admin'), 2),
         '1990-01-15', @rol_admin, DATEADD(YEAR, -2, GETDATE()), 1);

    SET @id_admin = SCOPE_IDENTITY();
END

/* ---------------------------------------------------------------------
   4. Equipos
   --------------------------------------------------------------------- */
DECLARE @equipo_base INT = (SELECT ISNULL(MAX(id_equipo), 0) FROM quiniela.Equipo);

INSERT INTO quiniela.Equipo (nombre, pais, ciudad, escudo_url)
SELECT
    CONCAT('Club Deportivo ',
           CHOOSE(1 + n % 10, 'Aurora','Boreal','Cóndor','Delta','Estrella',
                              'Fénix','Guaraní','Halcón','Ibérico','Jaguar'),
           ' ', n),
    CHOOSE(1 + n % 8, 'Costa Rica','México','España','Argentina',
                      'Colombia','Brasil','Chile','Uruguay'),
    CHOOSE(1 + n % 6, 'San José','Guadalajara','Valencia','Rosario','Medellín','Curitiba'),
    CONCAT('https://cdn.quinielas.local/escudos/', @equipo_base + n, '.png')
FROM #numeros
WHERE n <= @equipos_nuevos;

PRINT CONCAT('Equipos insertados: ', @@ROWCOUNT);

/* ---------------------------------------------------------------------
   5. Usuarios
   CHECK: correo LIKE '%_@_%._%' y fecha_nacimiento < hoy
   --------------------------------------------------------------------- */
DECLARE @usuario_base INT = (SELECT ISNULL(MAX(id_usuario), 0) FROM quiniela.Usuario);

INSERT INTO quiniela.Usuario
    (nombre_completo, correo, nombre_usuario, contrasena_hash,
     fecha_nacimiento, id_rol, fecha_registro, activo)
SELECT
    CONCAT(CHOOSE(1 + n % 12, 'Ana','Bruno','Carla','Diego','Elena','Fabián',
                              'Gabriela','Héctor','Irene','Joaquín','Karla','Luis'),
           ' ',
           CHOOSE(1 + (n / 12) % 10, 'Ramírez','Solano','Vargas','Chaves','Mora',
                                     'Jiménez','Araya','Bonilla','Quesada','Zúñiga')),
    CONCAT('jugador', @usuario_base + n, '@quinielas.test'),
    CONCAT('jugador_', @usuario_base + n),
    CONVERT(VARCHAR(255), HASHBYTES('SHA2_256', CONCAT('clave', n)), 2),
    DATEADD(DAY, -(6570 + (n * 7) % 12000), CAST(GETDATE() AS DATE)),
    @rol_jugador,
    DATEADD(DAY, -((n * 3) % 900), GETDATE()),
    CASE WHEN n % 25 = 0 THEN 0 ELSE 1 END
FROM #numeros
WHERE n <= @usuarios_nuevos;

PRINT CONCAT('Usuarios insertados: ', @@ROWCOUNT);

/* ---------------------------------------------------------------------
   6. Quinielas
   CHECK: estado IN (Abierta, Cerrada, Finalizada)
          modalidad IN (Publica, Privada)
          fecha_cierre > fecha_inicio, costo >= 0, premio >= 0
   --------------------------------------------------------------------- */
DECLARE @tipo_puntuacion INT =
    (SELECT TOP 1 id_tipo_puntuacion FROM quiniela.TipoPuntuacion ORDER BY id_tipo_puntuacion);

INSERT INTO quiniela.Quiniela
    (nombre, descripcion, reglas, fecha_inicio_inscripcion, fecha_cierre_inscripcion,
     estado, modalidad, id_tipo_puntuacion, id_admin_creador,
     costo_inscripcion, premio, fecha_creacion)
SELECT
    CONCAT('Torneo ',
           CHOOSE(1 + n % 5, 'Apertura','Clausura','Copa','Liga','Supercopa'),
           ' ', 2024 + (n % 3), ' #', n),
    CONCAT('Quiniela de prueba generada automáticamente para el torneo número ', n, '.'),
    'Se otorgan puntos por acertar el ganador, el marcador exacto y la diferencia de goles. '
    + 'Los pronósticos se cierran al iniciar cada partido.',
    DATEADD(DAY, -(400 - n * 12), GETDATE()),
    DATEADD(DAY, -(400 - n * 12) + 14, GETDATE()),
    CASE WHEN n <= @quinielas_nuevas - 6 THEN 'Finalizada'
         WHEN n <= @quinielas_nuevas - 2 THEN 'Cerrada'
         ELSE 'Abierta' END,
    CASE WHEN n % 4 = 0 THEN 'Privada' ELSE 'Publica' END,
    ISNULL(@tipo_puntuacion, 1),
    @id_admin,
    CAST(1000 * (1 + n % 5) AS DECIMAL(18, 2)),
    CAST(25000 * (1 + n % 8) AS DECIMAL(18, 2)),
    DATEADD(DAY, -(410 - n * 12), GETDATE())
FROM #numeros
WHERE n <= @quinielas_nuevas;

PRINT CONCAT('Quinielas insertadas: ', @@ROWCOUNT);

/* ---------------------------------------------------------------------
   7. Partidos
   CHECK: local <> visitante
          estado IN (Pendiente, EnCurso, Finalizado)
          goles ambos NULL o ambos >= 0
          si estado = Finalizado, goles NOT NULL
   --------------------------------------------------------------------- */
DECLARE @total_equipos INT = (SELECT COUNT(*) FROM quiniela.Equipo);

;WITH equipos_ordenados AS (
    SELECT id_equipo, ROW_NUMBER() OVER (ORDER BY id_equipo) - 1 AS pos
    FROM quiniela.Equipo
),
combinaciones AS (
    SELECT
        q.id_quiniela,
        num.n,
        q.estado                                        AS estado_quiniela,
        (num.n * 3) % @total_equipos                    AS pos_local,
        ((num.n * 3) + 1 + (num.n % (@total_equipos - 1))) % @total_equipos AS pos_visitante
    FROM quiniela.Quiniela q
    CROSS JOIN #numeros num
    WHERE num.n <= @partidos_por_quiniela
)
INSERT INTO quiniela.Partido
    (id_quiniela, id_equipo_local, id_equipo_visitante, fecha_hora,
     estado, goles_local, goles_visitante, fecha_registro_resultado)
SELECT
    c.id_quiniela,
    el.id_equipo,
    ev.id_equipo,
    DATEADD(HOUR, c.n * 6, DATEADD(DAY, -300 + (c.id_quiniela % 30) * 5, GETDATE())),
    estado.valor,
    CASE WHEN estado.valor = 'Finalizado' THEN (c.n * 7) % 5 ELSE NULL END,
    CASE WHEN estado.valor = 'Finalizado' THEN (c.n * 3) % 4 ELSE NULL END,
    CASE WHEN estado.valor = 'Finalizado'
         THEN DATEADD(HOUR, c.n * 6 + 2, DATEADD(DAY, -300 + (c.id_quiniela % 30) * 5, GETDATE()))
         ELSE NULL END
FROM combinaciones c
JOIN equipos_ordenados el ON el.pos = c.pos_local
JOIN equipos_ordenados ev ON ev.pos = c.pos_visitante
CROSS APPLY (
    SELECT CASE
        WHEN c.estado_quiniela = 'Finalizada' THEN 'Finalizado'
        WHEN c.n <= @partidos_por_quiniela - 10 THEN 'Finalizado'
        WHEN c.n <= @partidos_por_quiniela - 4  THEN 'EnCurso'
        ELSE 'Pendiente'
    END AS valor
) estado
WHERE el.id_equipo <> ev.id_equipo;

PRINT CONCAT('Partidos insertados: ', @@ROWCOUNT);

/* ---------------------------------------------------------------------
   8. Participaciones
   CHECK: estado_pago IN (N/A, Pagado, Pendiente), puntaje_total >= 0
   Un usuario participa como máximo una vez por quiniela.
   --------------------------------------------------------------------- */
;WITH usuarios_ordenados AS (
    SELECT id_usuario, ROW_NUMBER() OVER (ORDER BY id_usuario) AS pos
    FROM quiniela.Usuario
    WHERE id_rol = @rol_jugador
),
quinielas_ordenadas AS (
    SELECT id_quiniela, fecha_inicio_inscripcion,
           ROW_NUMBER() OVER (ORDER BY id_quiniela) AS pos
    FROM quiniela.Quiniela
)
INSERT INTO quiniela.Participacion
    (id_usuario, id_quiniela, fecha_inscripcion, reglas_aceptadas, estado_pago, puntaje_total)
SELECT
    u.id_usuario,
    q.id_quiniela,
    DATEADD(HOUR, (u.pos % 200), q.fecha_inicio_inscripcion),
    1,
    CHOOSE(1 + (u.pos + q.pos) % 3, 'Pagado', 'Pendiente', 'N/A'),
    0
FROM quinielas_ordenadas q
JOIN usuarios_ordenados u
  ON u.pos BETWEEN ((q.pos - 1) * 40) % 1800 + 1
              AND ((q.pos - 1) * 40) % 1800 + @participantes_por_quiniela
WHERE NOT EXISTS (
    SELECT 1 FROM quiniela.Participacion p
    WHERE p.id_usuario = u.id_usuario AND p.id_quiniela = q.id_quiniela
);

PRINT CONCAT('Participaciones insertadas: ', @@ROWCOUNT);

/* ---------------------------------------------------------------------
   9. Pronósticos (tabla de hechos: la que da volumen real)
   El trigger bloquea pronósticos sobre partidos iniciados, así que se
   desactiva temporalmente. Se reactiva en la sección 10.
   --------------------------------------------------------------------- */
DISABLE TRIGGER quiniela.tr_Pronostico_BloquearPartidoIniciado ON quiniela.Pronostico;

INSERT INTO quiniela.Pronostico
    (id_usuario, id_partido, goles_local_predichos, goles_visitante_predichos,
     fecha_ingreso, puntos_obtenidos)
SELECT
    pa.id_usuario,
    pt.id_partido,
    ABS(CHECKSUM(pa.id_usuario, pt.id_partido)) % 5,
    ABS(CHECKSUM(pt.id_partido, pa.id_usuario)) % 4,
    DATEADD(HOUR, -(ABS(CHECKSUM(pa.id_usuario, pt.id_partido)) % 72) - 2, pt.fecha_hora),
    CASE WHEN pt.estado = 'Finalizado'
         THEN CASE
                WHEN ABS(CHECKSUM(pa.id_usuario, pt.id_partido)) % 5 = pt.goles_local
                 AND ABS(CHECKSUM(pt.id_partido, pa.id_usuario)) % 4 = pt.goles_visitante
                THEN 5
                WHEN SIGN(ABS(CHECKSUM(pa.id_usuario, pt.id_partido)) % 5
                          - ABS(CHECKSUM(pt.id_partido, pa.id_usuario)) % 4)
                   = SIGN(pt.goles_local - pt.goles_visitante)
                THEN 3
                ELSE 0
              END
         ELSE 0 END
FROM quiniela.Participacion pa
JOIN quiniela.Partido pt ON pt.id_quiniela = pa.id_quiniela
WHERE NOT EXISTS (
    SELECT 1 FROM quiniela.Pronostico pr
    WHERE pr.id_usuario = pa.id_usuario AND pr.id_partido = pt.id_partido
);

PRINT CONCAT('Pronósticos insertados: ', @@ROWCOUNT);

/* ---------------------------------------------------------------------
   10. Reactivar el trigger y consolidar puntajes
   --------------------------------------------------------------------- */
ENABLE TRIGGER quiniela.tr_Pronostico_BloquearPartidoIniciado ON quiniela.Pronostico;

UPDATE pa
   SET puntaje_total = ISNULL(t.puntos, 0)
FROM quiniela.Participacion pa
OUTER APPLY (
    SELECT SUM(pr.puntos_obtenidos) AS puntos
    FROM quiniela.Pronostico pr
    JOIN quiniela.Partido pt ON pt.id_partido = pr.id_partido
    WHERE pr.id_usuario = pa.id_usuario AND pt.id_quiniela = pa.id_quiniela
) t;

PRINT CONCAT('Puntajes actualizados: ', @@ROWCOUNT);

/* ---------------------------------------------------------------------
   11. Resumen
   --------------------------------------------------------------------- */
DROP TABLE #numeros;

SELECT 'Equipo' AS tabla, COUNT(*) AS filas FROM quiniela.Equipo
UNION ALL SELECT 'Usuario',        COUNT(*) FROM quiniela.Usuario
UNION ALL SELECT 'Quiniela',       COUNT(*) FROM quiniela.Quiniela
UNION ALL SELECT 'Partido',        COUNT(*) FROM quiniela.Partido
UNION ALL SELECT 'Participacion',  COUNT(*) FROM quiniela.Participacion
UNION ALL SELECT 'Pronostico',     COUNT(*) FROM quiniela.Pronostico
ORDER BY filas DESC;

SELECT
    CAST(SUM(size) * 8.0 / 1024 AS DECIMAL(10,2))                                    AS tamano_total_mb,
    CAST(SUM(CASE WHEN type = 0 THEN size ELSE 0 END) * 8.0 / 1024 AS DECIMAL(10,2)) AS datos_mb,
    CAST(SUM(CASE WHEN type = 1 THEN size ELSE 0 END) * 8.0 / 1024 AS DECIMAL(10,2)) AS log_mb
FROM sys.database_files;

PRINT CONCAT('Carga completada en ',
             DATEDIFF(SECOND, @inicio, SYSDATETIME()), ' segundos.');
GO
