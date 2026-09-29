/* =========================================================================
   05_sample_data.sql
   Datos de prueba: roles, usuarios, tipos de puntuación, equipos,
   una quiniela con partidos, inscripciones y pronósticos.

   Contraseñas de prueba: se insertan hashes bcrypt precalculados.
       admin / Admin123!      -> administrador
       jugador1 / Jugador123!  -> jugador
       jugador2 / Jugador123!  -> jugador
       jugador3 / Jugador123!  -> jugador
   ========================================================================= */

USE QuinielasDB;
GO

/* ----- Catálogo: ROLES ----- */
INSERT INTO quiniela.Rol (nombre, descripcion) VALUES
    ('Administrador', 'Gestiona quinielas, partidos, equipos y resultados.'),
    ('Jugador',       'Se inscribe en quinielas, realiza pronósticos y consulta su ranking.');
GO

/* ----- Catálogo: TIPOS DE PUNTUACIÓN ----- */
INSERT INTO quiniela.TipoPuntuacion
    (nombre, descripcion, puntos_acierto_ganador, puntos_marcador_exacto, puntos_diferencia_goles)
VALUES
    ('Estándar',
     'Acierto al ganador = 1 pto, marcador exacto = 3 ptos.',
     1, 3, 0),
    ('Premium',
     'Ganador = 2, marcador exacto = 5, diferencia de goles = 1.',
     2, 5, 1),
    ('Clásico Mundial',
     'Ganador = 3, marcador exacto = 7.',
     3, 7, 0);
GO

/* ----- USUARIOS (hashes bcrypt pre-generados, cost=12) ----- */
-- Admin123!     -> $2b$12$TxX5lpl9RyQAeZfLl0izOeW0KRMQ64MN3f6I49LKdmo91lmwP4d3O
-- Jugador123!   -> $2b$12$vC4KgCrAK9uBqiZWWXxif.gycJaMabe1UijE8lxObqHIT/J6kOsGi
INSERT INTO quiniela.Usuario
    (nombre_completo, correo, nombre_usuario, contrasena_hash, fecha_nacimiento, id_rol)
VALUES
    ('Carla Administradora', 'admin@quinielas.cr',  'admin',
     '$2b$12$TxX5lpl9RyQAeZfLl0izOeW0KRMQ64MN3f6I49LKdmo91lmwP4d3O',
     '1990-03-15', 1),
    ('Luis Jugador Uno',     'luis@quinielas.cr',   'jugador1',
     '$2b$12$vC4KgCrAK9uBqiZWWXxif.gycJaMabe1UijE8lxObqHIT/J6kOsGi',
     '1995-07-22', 2),
    ('María Jugadora Dos',   'maria@quinielas.cr',  'jugador2',
     '$2b$12$vC4KgCrAK9uBqiZWWXxif.gycJaMabe1UijE8lxObqHIT/J6kOsGi',
     '1992-11-03', 2),
    ('Diego Jugador Tres',   'diego@quinielas.cr',  'jugador3',
     '$2b$12$vC4KgCrAK9uBqiZWWXxif.gycJaMabe1UijE8lxObqHIT/J6kOsGi',
     '1998-01-28', 2);
GO

/* ----- EQUIPOS ----- */
INSERT INTO quiniela.Equipo (nombre, pais, ciudad) VALUES
    ('Deportivo Saprissa',        'Costa Rica', 'San José'),
    ('LD Alajuelense',            'Costa Rica', 'Alajuela'),
    ('Club Sport Herediano',      'Costa Rica', 'Heredia'),
    ('Cartaginés',                'Costa Rica', 'Cartago'),
    ('Pérez Zeledón',             'Costa Rica', 'San Isidro'),
    ('Puntarenas FC',             'Costa Rica', 'Puntarenas'),
    ('Municipal Liberia',         'Costa Rica', 'Liberia'),
    ('Guanacasteca',              'Costa Rica', 'Nicoya');
GO

/* ----- QUINIELA ----- */
INSERT INTO quiniela.Quiniela
    (nombre, descripcion, reglas,
     fecha_inicio_inscripcion, fecha_cierre_inscripcion,
     estado, modalidad, id_tipo_puntuacion, id_admin_creador,
     costo_inscripcion, premio)
VALUES
    ('Torneo Apertura 2026',
     'Quiniela oficial de la primera vuelta del torneo nacional.',
     '1. Pronósticos válidos antes del inicio de cada partido. ' +
     '2. Un pronóstico por partido por usuario. ' +
     '3. Puntuación: 1 pto ganador, 3 ptos marcador exacto.',
     DATEADD(DAY, -10, GETDATE()),
     DATEADD(DAY, 30, GETDATE()),
     'Abierta', 'Publica', 1, 1, 0, 0);
GO

/* ----- PARTIDOS ----- */
DECLARE @idQ INT = (SELECT id_quiniela FROM quiniela.Quiniela WHERE nombre = 'Torneo Apertura 2026');

INSERT INTO quiniela.Partido
    (id_quiniela, id_equipo_local, id_equipo_visitante, fecha_hora, estado,
     goles_local, goles_visitante, fecha_registro_resultado)
VALUES
    (@idQ, 1, 2, DATEADD(DAY, -5, GETDATE()),  'Finalizado', 2, 1, DATEADD(DAY, -5, GETDATE())),
    (@idQ, 3, 4, DATEADD(DAY, -4, GETDATE()),  'Finalizado', 1, 1, DATEADD(DAY, -4, GETDATE())),
    (@idQ, 5, 6, DATEADD(DAY, -3, GETDATE()),  'Finalizado', 0, 2, DATEADD(DAY, -3, GETDATE())),
    (@idQ, 7, 8, DATEADD(DAY,  3, GETDATE()),  'Pendiente',  NULL, NULL, NULL),
    (@idQ, 1, 3, DATEADD(DAY,  5, GETDATE()),  'Pendiente',  NULL, NULL, NULL),
    (@idQ, 2, 4, DATEADD(DAY,  7, GETDATE()),  'Pendiente',  NULL, NULL, NULL);
GO

/* ----- INSCRIPCIONES ----- */
INSERT INTO quiniela.Participacion (id_usuario, id_quiniela, reglas_aceptadas, estado_pago)
SELECT u.id_usuario, q.id_quiniela, 1, 'N/A'
FROM quiniela.Usuario u
CROSS JOIN quiniela.Quiniela q
WHERE u.id_rol = 2
  AND q.nombre = 'Torneo Apertura 2026';
GO

/* ----- PRONÓSTICOS (sobre partidos ya finalizados para ver el cálculo) ----- */
-- El trigger bloquea pronósticos en partidos iniciados, así que insertamos
-- directamente desactivando el trigger temporalmente para datos históricos.
DISABLE TRIGGER quiniela.tr_Pronostico_BloquearPartidoIniciado ON quiniela.Pronostico;
GO

DECLARE @u1 INT = (SELECT id_usuario FROM quiniela.Usuario WHERE nombre_usuario = 'jugador1');
DECLARE @u2 INT = (SELECT id_usuario FROM quiniela.Usuario WHERE nombre_usuario = 'jugador2');
DECLARE @u3 INT = (SELECT id_usuario FROM quiniela.Usuario WHERE nombre_usuario = 'jugador3');

DECLARE @p1 INT, @p2 INT, @p3 INT;
SELECT TOP 1 @p1 = id_partido FROM quiniela.Partido WHERE estado = 'Finalizado' ORDER BY fecha_hora;
SELECT @p2 = id_partido FROM quiniela.Partido WHERE estado = 'Finalizado' ORDER BY fecha_hora OFFSET 1 ROW FETCH NEXT 1 ROW ONLY;
SELECT @p3 = id_partido FROM quiniela.Partido WHERE estado = 'Finalizado' ORDER BY fecha_hora OFFSET 2 ROW FETCH NEXT 1 ROW ONLY;

INSERT INTO quiniela.Pronostico (id_usuario, id_partido, goles_local_predichos, goles_visitante_predichos)
VALUES
    (@u1, @p1, 2, 1),  -- acierta marcador exacto (partido 1: 2-1)
    (@u1, @p2, 1, 0),  -- falla (real 1-1)
    (@u1, @p3, 0, 1),  -- acierta ganador       (partido 3: 0-2)
    (@u2, @p1, 1, 0),  -- acierta ganador
    (@u2, @p2, 1, 1),  -- acierta marcador exacto
    (@u2, @p3, 1, 2),  -- acierta ganador
    (@u3, @p1, 0, 0),  -- falla
    (@u3, @p2, 2, 0),  -- falla
    (@u3, @p3, 0, 2);  -- acierta marcador exacto
GO


/* Recalcular los puntajes de los partidos finalizados */
DECLARE @id INT;
DECLARE cur CURSOR FOR
    SELECT id_partido FROM quiniela.Partido WHERE estado = 'Finalizado';
OPEN cur;
FETCH NEXT FROM cur INTO @id;
WHILE @@FETCH_STATUS = 0
BEGIN
    EXEC quiniela.sp_CalcularPuntosPartido @id;
    FETCH NEXT FROM cur INTO @id;
END
CLOSE cur;
DEALLOCATE cur;
GO

ENABLE TRIGGER quiniela.tr_Pronostico_BloquearPartidoIniciado ON quiniela.Pronostico;
GO

PRINT 'Datos de prueba cargados y puntajes calculados.';
PRINT 'Usuarios de prueba:';
PRINT '   admin    / Admin123!';
PRINT '   jugador1 / Jugador123!';
PRINT '   jugador2 / Jugador123!';
PRINT '   jugador3 / Jugador123!';
GO
