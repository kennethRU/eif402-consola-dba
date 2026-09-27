/* =========================================================================
   01_create_database.sql
   Proyecto: Sistema de Quinielas de Fútbol
   Curso:    EIF211 - Diseño e Implementación de Bases de Datos
   DBMS:     SQL Server
   Descripción: Crea la base de datos QuinielasDB y su esquema principal.
   ========================================================================= */

USE master;
GO

-- Si la BD ya existe, la elimina (ideal para pruebas)
IF DB_ID('QuinielasDB') IS NOT NULL
BEGIN
    ALTER DATABASE QuinielasDB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE QuinielasDB;
END
GO

CREATE DATABASE QuinielasDB
    COLLATE Modern_Spanish_CI_AI;
GO

USE QuinielasDB;
GO

-- Esquema lógico para organizar objetos
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'quiniela')
    EXEC('CREATE SCHEMA quiniela');
GO

PRINT 'Base de datos QuinielasDB creada correctamente.';
GO
