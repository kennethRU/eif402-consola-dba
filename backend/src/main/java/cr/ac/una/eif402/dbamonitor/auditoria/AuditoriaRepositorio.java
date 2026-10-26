package cr.ac.una.eif402.dbamonitor.auditoria;

import cr.ac.una.eif402.dbamonitor.auditoria.AuditoriaDtos.*;
import cr.ac.una.eif402.dbamonitor.common.RepositorioAdministrativo;
import cr.ac.una.eif402.dbamonitor.conexion.ProveedorConexiones;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Consultas administrativas del Modulo 5.
 * Fuentes: sys.server_principals, sys.database_principals,
 * sys.database_role_members, sys.server_role_members,
 * sys.database_permissions, sys.sql_expression_dependencies.
 */
@Repository
public class AuditoriaRepositorio extends RepositorioAdministrativo {

    public AuditoriaRepositorio(ProveedorConexiones proveedor) {
        super(proveedor);
    }

    private static final String SQL_LOGINS = """
            SELECT sp.name                  AS login,
                   sp.type_desc             AS tipo,
                   sp.is_disabled           AS deshabilitado,
                   sp.create_date           AS fecha_creacion,
                   sp.modify_date           AS fecha_modificacion,
                   sp.default_database_name AS base_datos_predeterminada,
                   ISNULL(IS_SRVROLEMEMBER('sysadmin', sp.name), 0) AS es_sysadmin,
                   CAST(CASE WHEN EXISTS (
                            SELECT 1
                            FROM sys.server_role_members srm
                            JOIN sys.server_principals r ON r.principal_id = srm.role_principal_id
                            WHERE r.name = 'sysadmin' AND srm.member_principal_id = sp.principal_id)
                        THEN 1 ELSE 0 END AS BIT) AS sysadmin_directo
            FROM sys.server_principals sp
            WHERE sp.type IN ('S','U','G') AND sp.name NOT LIKE '##%'
            ORDER BY sp.name
            """;

    private static final String SQL_USUARIOS = """
            SELECT dp.name                     AS usuario,
                   dp.type_desc                AS tipo,
                   dp.create_date              AS fecha_creacion,
                   dp.default_schema_name      AS esquema_predeterminado,
                   sp.name                     AS login_asociado,
                   dp.authentication_type_desc AS tipo_autenticacion
            FROM sys.database_principals dp
            LEFT JOIN sys.server_principals sp ON sp.sid = dp.sid
            WHERE dp.type IN ('S','U','G')
              AND dp.name NOT IN ('guest','INFORMATION_SCHEMA','sys')
            ORDER BY dp.name
            """;

    /*
     * Los principales de servidor usan la intercalacion de la instancia y los
     * de base de datos la de la base. Si ambas difieren, el UNION ALL falla con
     * el error 451 al ordenar por una columna de texto. COLLATE DATABASE_DEFAULT
     * normaliza ambos lados sin alterar la configuracion del servidor.
     */
    private static final String SQL_ROLES = """
            SELECT N'Base de datos'                        AS ambito,
                   rol.name          COLLATE DATABASE_DEFAULT AS rol,
                   miembro.name      COLLATE DATABASE_DEFAULT AS miembro,
                   miembro.type_desc COLLATE DATABASE_DEFAULT AS tipo_miembro
            FROM sys.database_role_members drm
            JOIN sys.database_principals rol     ON rol.principal_id     = drm.role_principal_id
            JOIN sys.database_principals miembro ON miembro.principal_id = drm.member_principal_id
            UNION ALL
            SELECT N'Servidor',
                   rol.name          COLLATE DATABASE_DEFAULT,
                   miembro.name      COLLATE DATABASE_DEFAULT,
                   miembro.type_desc COLLATE DATABASE_DEFAULT
            FROM sys.server_role_members srm
            JOIN sys.server_principals rol     ON rol.principal_id     = srm.role_principal_id
            JOIN sys.server_principals miembro ON miembro.principal_id = srm.member_principal_id
            ORDER BY ambito, rol, miembro
            """;

    private static final String SQL_PRIVILEGIOS = """
            SELECT pr.name            AS beneficiario,
                   pr.type_desc       AS tipo_beneficiario,
                   pe.state_desc      AS estado,
                   pe.permission_name AS privilegio,
                   pe.class_desc      AS clase,
                   CASE pe.class
                        WHEN 0 THEN DB_NAME()
                        WHEN 1 THEN ISNULL(OBJECT_SCHEMA_NAME(pe.major_id) + N'.' + OBJECT_NAME(pe.major_id), N'(objeto)')
                        WHEN 3 THEN SCHEMA_NAME(pe.major_id)
                        ELSE CAST(pe.major_id AS NVARCHAR(20))
                   END AS objeto
            FROM sys.database_permissions pe
            JOIN sys.database_principals pr ON pr.principal_id = pe.grantee_principal_id
            WHERE pr.name NOT IN ('public','guest')
            ORDER BY pr.name, pe.permission_name
            """;

    /*
     * "inserted" y "deleted" son pseudo-tablas que solo existen durante la
     * ejecucion de un trigger: no estan en el catalogo, asi que OBJECT_ID
     * devuelve NULL y aparecerian como dependencias rotas. Se excluyen para
     * evitar falsos positivos sobre triggers que estan perfectamente sanos.
     */
    private static final String SQL_OBJETOS_PROBLEMA = """
            SELECT OBJECT_SCHEMA_NAME(sed.referencing_id) AS esquema,
                   OBJECT_NAME(sed.referencing_id)        AS objeto,
                   o.type_desc                            AS tipo_objeto,
                   ISNULL(sed.referenced_schema_name, N'dbo') + N'.' + sed.referenced_entity_name AS entidad_referenciada,
                   N'La entidad referenciada no existe en la base de datos' AS diagnostico
            FROM sys.sql_expression_dependencies sed
            JOIN sys.objects o ON o.object_id = sed.referencing_id
            WHERE sed.referenced_id IS NULL
              AND sed.is_ambiguous = 0
              AND sed.referenced_server_name IS NULL
              AND sed.referenced_database_name IS NULL
              AND OBJECT_ID(QUOTENAME(ISNULL(sed.referenced_schema_name, N'dbo')) + N'.'
                            + QUOTENAME(sed.referenced_entity_name)) IS NULL
              AND sed.referenced_entity_name NOT IN (N'inserted', N'deleted')
              AND o.is_ms_shipped = 0
            """;

    public List<LoginServidor> logins() {
        return consultar(SQL_LOGINS, (rs, fila) -> new LoginServidor(
                rs.getString("login"),
                rs.getString("tipo"),
                rs.getBoolean("deshabilitado"),
                aFechaHora(rs.getTimestamp("fecha_creacion")),
                aFechaHora(rs.getTimestamp("fecha_modificacion")),
                rs.getString("base_datos_predeterminada"),
                rs.getBoolean("es_sysadmin"),
                rs.getBoolean("sysadmin_directo")));
    }

    public List<UsuarioBaseDatos> usuarios() {
        return consultar(SQL_USUARIOS, (rs, fila) -> new UsuarioBaseDatos(
                rs.getString("usuario"),
                rs.getString("tipo"),
                aFechaHora(rs.getTimestamp("fecha_creacion")),
                rs.getString("esquema_predeterminado"),
                rs.getString("login_asociado"),
                rs.getString("tipo_autenticacion")));
    }

    public List<AsignacionRol> roles() {
        return consultar(SQL_ROLES, (rs, fila) -> new AsignacionRol(
                rs.getString("ambito"),
                rs.getString("rol"),
                rs.getString("miembro"),
                rs.getString("tipo_miembro")));
    }

    public List<Privilegio> privilegios() {
        return consultar(SQL_PRIVILEGIOS, (rs, fila) -> new Privilegio(
                rs.getString("beneficiario"),
                rs.getString("tipo_beneficiario"),
                rs.getString("estado"),
                rs.getString("privilegio"),
                rs.getString("clase"),
                rs.getString("objeto")));
    }

    public List<ObjetoConProblema> objetosConProblemas() {
        return consultar(SQL_OBJETOS_PROBLEMA, (rs, fila) -> new ObjetoConProblema(
                rs.getString("esquema"),
                rs.getString("objeto"),
                rs.getString("tipo_objeto"),
                rs.getString("entidad_referenciada"),
                rs.getString("diagnostico")));
    }
}