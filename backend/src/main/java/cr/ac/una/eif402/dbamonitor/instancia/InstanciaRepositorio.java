package cr.ac.una.eif402.dbamonitor.instancia;

import cr.ac.una.eif402.dbamonitor.common.RepositorioAdministrativo;
import cr.ac.una.eif402.dbamonitor.conexion.ProveedorConexiones;
import cr.ac.una.eif402.dbamonitor.instancia.InstanciaDtos.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Consultas administrativas del Modulo 1.
 * Fuentes: SERVERPROPERTY, sys.dm_os_sys_info, sys.dm_server_services,
 * sys.databases, sys.dm_os_memory_clerks.
 */
@Repository
public class InstanciaRepositorio extends RepositorioAdministrativo {

    public InstanciaRepositorio(ProveedorConexiones proveedor) {
        super(proveedor);
    }

    private static final String SQL_ESTADO = """
            SELECT
                CAST(SERVERPROPERTY('MachineName')    AS NVARCHAR(128)) AS nombre_servidor,
                CAST(SERVERPROPERTY('ServerName')     AS NVARCHAR(128)) AS nombre_completo_instancia,
                ISNULL(CAST(SERVERPROPERTY('InstanceName') AS NVARCHAR(128)), N'MSSQLSERVER') AS nombre_instancia,
                @@SERVICENAME                                           AS nombre_servicio,
                CAST(SERVERPROPERTY('Edition')        AS NVARCHAR(128)) AS edicion,
                CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(128)) AS version_producto,
                CAST(SERVERPROPERTY('ProductLevel')   AS NVARCHAR(128)) AS nivel_producto,
                LEFT(@@VERSION, CHARINDEX(CHAR(10), @@VERSION + CHAR(10)) - 1) AS descripcion_version,
                CAST(SERVERPROPERTY('Collation')      AS NVARCHAR(128)) AS intercalacion,
                si.sqlserver_start_time                                  AS fecha_inicio,
                DATEDIFF(SECOND, si.sqlserver_start_time, SYSDATETIME()) AS segundos_actividad,
                si.cpu_count                                             AS cantidad_cpu,
                CAST(si.physical_memory_kb  / 1024.0 AS DECIMAL(18,2))   AS memoria_fisica_mb,
                CAST(si.committed_target_kb / 1024.0 AS DECIMAL(18,2))   AS memoria_objetivo_mb,
                CAST(si.committed_kb        / 1024.0 AS DECIMAL(18,2))   AS memoria_utilizada_mb
            FROM sys.dm_os_sys_info si
            """;

    private static final String SQL_SERVICIOS = """
            SELECT servicename        AS nombre_servicio,
                   status_desc        AS estado,
                   startup_type_desc  AS tipo_inicio,
                   process_id         AS id_proceso,
                   last_startup_time  AS ultimo_arranque,
                   service_account    AS cuenta_servicio
            FROM sys.dm_server_services
            """;

    private static final String SQL_BASES_DATOS = """
            SELECT d.database_id         AS id_base_datos,
                   d.name                AS nombre,
                   d.state_desc          AS estado,
                   d.recovery_model_desc AS modelo_recuperacion,
                   d.compatibility_level AS nivel_compatibilidad,
                   d.collation_name      AS intercalacion,
                   d.create_date         AS fecha_creacion,
                   d.is_read_only        AS solo_lectura,
                   CAST(SUM(mf.size) * 8.0 / 1024 AS DECIMAL(18,2)) AS tamano_total_mb
            FROM sys.databases d
            LEFT JOIN sys.master_files mf ON mf.database_id = d.database_id
            GROUP BY d.database_id, d.name, d.state_desc, d.recovery_model_desc,
                     d.compatibility_level, d.collation_name, d.create_date, d.is_read_only
            ORDER BY d.name
            """;

    private static final String SQL_MEMORIA_COMPONENTES = """
            SELECT TOP (10)
                   type AS componente,
                   CAST(SUM(pages_kb) / 1024.0 AS DECIMAL(18,2)) AS memoria_mb
            FROM sys.dm_os_memory_clerks
            GROUP BY type
            HAVING SUM(pages_kb) > 0
            ORDER BY SUM(pages_kb) DESC
            """;

    public Optional<EstadoInstancia> obtenerEstado() {
        return consultarUno(SQL_ESTADO, (rs, fila) -> new EstadoInstancia(
                rs.getString("nombre_servidor"),
                rs.getString("nombre_completo_instancia"),
                rs.getString("nombre_instancia"),
                rs.getString("nombre_servicio"),
                rs.getString("edicion"),
                rs.getString("version_producto"),
                rs.getString("nivel_producto"),
                rs.getString("descripcion_version"),
                rs.getString("intercalacion"),
                "ONLINE",
                rs.getTimestamp("fecha_inicio").toLocalDateTime(),
                rs.getLong("segundos_actividad"),
                null,
                rs.getInt("cantidad_cpu"),
                rs.getBigDecimal("memoria_fisica_mb"),
                rs.getBigDecimal("memoria_objetivo_mb"),
                rs.getBigDecimal("memoria_utilizada_mb"),
                null));
    }

    public List<EstadoServicio> listarServicios() {
        return consultar(SQL_SERVICIOS, (rs, fila) -> new EstadoServicio(
                rs.getString("nombre_servicio"),
                rs.getString("estado"),
                rs.getString("tipo_inicio"),
                rs.getObject("id_proceso") == null ? null : rs.getInt("id_proceso"),
                rs.getTimestamp("ultimo_arranque") == null
                        ? null : rs.getTimestamp("ultimo_arranque").toLocalDateTime(),
                rs.getString("cuenta_servicio")));
    }

    public List<BaseDatosAdministrada> listarBasesDatos() {
        return consultar(SQL_BASES_DATOS, (rs, fila) -> new BaseDatosAdministrada(
                rs.getInt("id_base_datos"),
                rs.getString("nombre"),
                rs.getString("estado"),
                rs.getString("modelo_recuperacion"),
                rs.getInt("nivel_compatibilidad"),
                rs.getString("intercalacion"),
                rs.getTimestamp("fecha_creacion").toLocalDateTime(),
                rs.getBoolean("solo_lectura"),
                rs.getBigDecimal("tamano_total_mb")));
    }

    public List<ComponenteMemoria> distribucionMemoria() {
        return consultar(SQL_MEMORIA_COMPONENTES, (rs, fila) -> new ComponenteMemoria(
                rs.getString("componente"),
                rs.getBigDecimal("memoria_mb")));
    }
}
