package cr.ac.una.eif402.dbamonitor.rendimiento;

import cr.ac.una.eif402.dbamonitor.common.RepositorioAdministrativo;
import cr.ac.una.eif402.dbamonitor.conexion.ProveedorConexiones;
import cr.ac.una.eif402.dbamonitor.rendimiento.RendimientoDtos.*;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Consultas administrativas del Modulo 2.
 * Fuentes: sys.dm_exec_query_stats, sys.dm_exec_sql_text,
 * sys.dm_exec_sessions, sys.dm_exec_requests, sys.dm_os_wait_stats.
 */
@Repository
public class RendimientoRepositorio extends RepositorioAdministrativo {

    /** Columnas por las que se permite ordenar las consultas costosas. */
    public static final List<String> ORDENAMIENTOS_PERMITIDOS =
            List.of("total_worker_time", "total_elapsed_time", "total_logical_reads", "execution_count");

    public RendimientoRepositorio(ProveedorConexiones proveedor) {
        super(proveedor);
    }

    private static final String SQL_CONSULTAS_COSTOSAS = """
            SELECT TOP (?)
                   DB_NAME(st.dbid)                                        AS base_datos,
                   qs.execution_count                                      AS ejecuciones,
                   CAST(qs.total_worker_time  / 1000.0 AS DECIMAL(18,2))   AS cpu_total_ms,
                   CAST(qs.total_worker_time  / 1000.0 / qs.execution_count AS DECIMAL(18,2)) AS cpu_promedio_ms,
                   CAST(qs.total_elapsed_time / 1000.0 AS DECIMAL(18,2))   AS duracion_total_ms,
                   CAST(qs.total_elapsed_time / 1000.0 / qs.execution_count AS DECIMAL(18,2)) AS duracion_promedio_ms,
                   qs.total_logical_reads                                  AS lecturas_logicas_total,
                   qs.total_logical_reads / qs.execution_count             AS lecturas_logicas_promedio,
                   qs.total_logical_writes                                 AS escrituras_total,
                   qs.last_execution_time                                  AS ultima_ejecucion,
                   SUBSTRING(st.text,
                             (qs.statement_start_offset / 2) + 1,
                             ((CASE qs.statement_end_offset
                                    WHEN -1 THEN DATALENGTH(st.text)
                                    ELSE qs.statement_end_offset
                               END - qs.statement_start_offset) / 2) + 1)  AS texto_consulta
            FROM sys.dm_exec_query_stats qs
            CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) st
            WHERE qs.execution_count > 0
            ORDER BY qs.%s DESC
            """;

    private static final String SQL_RESUMEN_EJECUCION = """
            SELECT COUNT(*)                                                   AS consultas_en_cache,
                   ISNULL(SUM(qs.execution_count), 0)                         AS ejecuciones_totales,
                   CAST(ISNULL(SUM(qs.total_elapsed_time), 0) / 1000.0 AS DECIMAL(18,2)) AS duracion_acumulada_ms,
                   CAST(ISNULL(SUM(qs.total_elapsed_time), 0) / 1000.0
                        / NULLIF(SUM(qs.execution_count), 0) AS DECIMAL(18,2)) AS duracion_promedio_ms,
                   CAST(ISNULL(SUM(qs.total_worker_time), 0) / 1000.0 AS DECIMAL(18,2))  AS cpu_acumulado_ms
            FROM sys.dm_exec_query_stats qs
            """;

    private static final String SQL_SESIONES = """
            SELECT s.session_id              AS id_sesion,
                   s.login_name              AS usuario,
                   s.host_name               AS equipo_cliente,
                   s.program_name            AS aplicacion,
                   s.status                  AS estado,
                   DB_NAME(s.database_id)    AS base_datos,
                   s.cpu_time                AS cpu_ms,
                   s.memory_usage * 8        AS memoria_kb,
                   s.reads                   AS lecturas,
                   s.writes                  AS escrituras,
                   s.login_time              AS inicio_sesion,
                   s.last_request_start_time AS ultima_solicitud,
                   c.client_net_address      AS direccion_cliente,
                   r.blocking_session_id     AS sesion_bloqueadora,
                   r.wait_type               AS tipo_espera,
                   r.wait_time               AS tiempo_espera_ms,
                   r.command                 AS comando_actual
            FROM sys.dm_exec_sessions s
            LEFT JOIN sys.dm_exec_connections c ON c.session_id = s.session_id
            LEFT JOIN sys.dm_exec_requests    r ON r.session_id = s.session_id
            WHERE s.is_user_process = 1
            ORDER BY s.cpu_time DESC
            """;

    private static final String SQL_SESIONES_BLOQUEADAS = """
            SELECT r.session_id           AS sesion_bloqueada,
                   r.blocking_session_id  AS sesion_bloqueadora,
                   r.wait_type            AS tipo_espera,
                   r.wait_time            AS tiempo_espera_ms,
                   r.wait_resource        AS recurso_esperado,
                   r.status               AS estado,
                   r.command              AS comando,
                   DB_NAME(r.database_id) AS base_datos,
                   sb.login_name          AS usuario_bloqueador,
                   sb.program_name        AS aplicacion_bloqueadora,
                   tb.text                AS consulta_bloqueada,
                   tv.text                AS consulta_bloqueadora
            FROM sys.dm_exec_requests r
            LEFT JOIN sys.dm_exec_sessions    sb ON sb.session_id = r.blocking_session_id
            LEFT JOIN sys.dm_exec_connections cv ON cv.session_id = r.blocking_session_id
            OUTER APPLY sys.dm_exec_sql_text(r.sql_handle)              tb
            OUTER APPLY sys.dm_exec_sql_text(cv.most_recent_sql_handle) tv
            WHERE r.blocking_session_id <> 0
            """;

    private static final String SQL_ESPERAS = """
            SELECT TOP (10)
                   wait_type                                           AS tipo_espera,
                   waiting_tasks_count                                 AS tareas_en_espera,
                   CAST(wait_time_ms / 1000.0 AS DECIMAL(18,2))        AS espera_total_seg,
                   CAST(signal_wait_time_ms / 1000.0 AS DECIMAL(18,2)) AS espera_cpu_seg,
                   CAST(100.0 * wait_time_ms / NULLIF(SUM(wait_time_ms) OVER (), 0) AS DECIMAL(5,2)) AS porcentaje
            FROM sys.dm_os_wait_stats
            WHERE wait_type NOT IN (
                    N'CLR_SEMAPHORE', N'LAZYWRITER_SLEEP', N'RESOURCE_QUEUE', N'SLEEP_TASK',
                    N'SLEEP_SYSTEMTASK', N'SQLTRACE_BUFFER_FLUSH', N'WAITFOR', N'XE_TIMER_EVENT',
                    N'BROKER_TASK_STOP', N'CHECKPOINT_QUEUE', N'REQUEST_FOR_DEADLOCK_SEARCH',
                    N'XE_DISPATCHER_WAIT', N'BROKER_TO_FLUSH', N'DIRTY_PAGE_POLL',
                    N'HADR_FILESTREAM_IOMGR_IOCOMPLETION', N'SP_SERVER_DIAGNOSTICS_SLEEP',
                    N'QDS_PERSIST_TASK_MAIN_LOOP_SLEEP', N'QDS_SHUTDOWN_QUEUE')
              AND wait_time_ms > 0
            ORDER BY wait_time_ms DESC
            """;

    public List<ConsultaCostosa> consultasCostosas(Integer cantidad, String ordenarPor) {
        int limite = limitar(cantidad, 20, 100);
        String columna = validarValorPermitido(ordenarPor, ORDENAMIENTOS_PERMITIDOS, "total_worker_time");
        String sql = String.format(SQL_CONSULTAS_COSTOSAS, columna);
        return consultar(sql, (rs, fila) -> new ConsultaCostosa(
                rs.getString("base_datos"),
                rs.getLong("ejecuciones"),
                rs.getBigDecimal("cpu_total_ms"),
                rs.getBigDecimal("cpu_promedio_ms"),
                rs.getBigDecimal("duracion_total_ms"),
                rs.getBigDecimal("duracion_promedio_ms"),
                rs.getLong("lecturas_logicas_total"),
                rs.getLong("lecturas_logicas_promedio"),
                rs.getLong("escrituras_total"),
                aFechaHora(rs.getTimestamp("ultima_ejecucion")),
                rs.getString("texto_consulta")), limite);
    }

    public Optional<ResumenEjecucion> resumenEjecucion() {
        return consultarUno(SQL_RESUMEN_EJECUCION, (rs, fila) -> new ResumenEjecucion(
                rs.getLong("consultas_en_cache"),
                rs.getLong("ejecuciones_totales"),
                rs.getBigDecimal("duracion_acumulada_ms"),
                rs.getBigDecimal("duracion_promedio_ms"),
                rs.getBigDecimal("cpu_acumulado_ms")));
    }

    public List<SesionActiva> sesionesActivas() {
        return consultar(SQL_SESIONES, (rs, fila) -> new SesionActiva(
                rs.getInt("id_sesion"),
                rs.getString("usuario"),
                rs.getString("equipo_cliente"),
                rs.getString("aplicacion"),
                rs.getString("estado"),
                rs.getString("base_datos"),
                rs.getLong("cpu_ms"),
                rs.getLong("memoria_kb"),
                rs.getLong("lecturas"),
                rs.getLong("escrituras"),
                aFechaHora(rs.getTimestamp("inicio_sesion")),
                aFechaHora(rs.getTimestamp("ultima_solicitud")),
                rs.getString("direccion_cliente"),
                rs.getObject("sesion_bloqueadora") == null
                        ? null : rs.getInt("sesion_bloqueadora"),
                rs.getString("tipo_espera"),
                rs.getObject("tiempo_espera_ms") == null ? null : rs.getLong("tiempo_espera_ms"),
                rs.getString("comando_actual")));
    }

    public List<SesionBloqueada> sesionesBloqueadas() {
        return consultar(SQL_SESIONES_BLOQUEADAS, (rs, fila) -> new SesionBloqueada(
                rs.getInt("sesion_bloqueada"),
                rs.getInt("sesion_bloqueadora"),
                rs.getString("tipo_espera"),
                rs.getLong("tiempo_espera_ms"),
                rs.getString("recurso_esperado"),
                rs.getString("estado"),
                rs.getString("comando"),
                rs.getString("base_datos"),
                rs.getString("usuario_bloqueador"),
                rs.getString("aplicacion_bloqueadora"),
                rs.getString("consulta_bloqueada"),
                rs.getString("consulta_bloqueadora")));
    }

    public List<TipoEspera> principalesEsperas() {
        return consultar(SQL_ESPERAS, (rs, fila) -> new TipoEspera(
                rs.getString("tipo_espera"),
                rs.getLong("tareas_en_espera"),
                rs.getBigDecimal("espera_total_seg"),
                rs.getBigDecimal("espera_cpu_seg"),
                rs.getBigDecimal("porcentaje")));
    }

    private static java.time.LocalDateTime aFechaHora(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }
}
