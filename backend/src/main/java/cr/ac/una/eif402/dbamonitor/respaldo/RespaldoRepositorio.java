package cr.ac.una.eif402.dbamonitor.respaldo;

import cr.ac.una.eif402.dbamonitor.common.RepositorioAdministrativo;
import cr.ac.una.eif402.dbamonitor.conexion.ProveedorConexiones;
import cr.ac.una.eif402.dbamonitor.respaldo.RespaldoDtos.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas administrativas del Modulo 4.
 * Fuentes: msdb.dbo.backupset, msdb.dbo.backupmediafamily,
 * msdb.dbo.sysjobs, msdb.dbo.sysjobhistory.
 *
 * backupset registra unicamente respaldos finalizados con exito; los
 * fallos se identifican en el historial de jobs del Agente SQL Server.
 */
@Repository
public class RespaldoRepositorio extends RepositorioAdministrativo {

    public RespaldoRepositorio(ProveedorConexiones proveedor) {
        super(proveedor);
    }

    private static final String SQL_HISTORIAL = """
            SELECT TOP (?)
                   bs.database_name  AS base_datos,
                   bs.backup_start_date  AS inicio,
                   bs.backup_finish_date AS fin,
                   DATEDIFF(SECOND, bs.backup_start_date, bs.backup_finish_date) AS duracion_seg,
                   CASE bs.type WHEN 'D' THEN N'Completo'
                                WHEN 'I' THEN N'Diferencial'
                                WHEN 'L' THEN N'Log de transacciones'
                                WHEN 'F' THEN N'Archivo o filegroup'
                                WHEN 'G' THEN N'Diferencial de archivo'
                                WHEN 'P' THEN N'Parcial'
                                WHEN 'Q' THEN N'Diferencial parcial'
                                ELSE N'Otro' END AS tipo_respaldo,
                   bs.recovery_model AS modelo_recuperacion,
                   CAST(bs.backup_size / 1024.0 / 1024 AS DECIMAL(18,2)) AS tamano_mb,
                   CAST(bs.compressed_backup_size / 1024.0 / 1024 AS DECIMAL(18,2)) AS tamano_comprimido_mb,
                   bs.is_copy_only   AS solo_copia,
                   bs.user_name      AS usuario,
                   mf.physical_device_name AS dispositivo
            FROM msdb.dbo.backupset bs
            LEFT JOIN msdb.dbo.backupmediafamily mf ON mf.media_set_id = bs.media_set_id
            WHERE (CAST(? AS NVARCHAR(128)) IS NULL
                   OR bs.database_name COLLATE DATABASE_DEFAULT = CAST(? AS NVARCHAR(128)))
            ORDER BY bs.backup_finish_date DESC
            """;

    private static final String SQL_ULTIMOS = """
            SELECT d.name AS base_datos,
                   d.recovery_model_desc AS modelo_recuperacion,
                   MAX(CASE WHEN bs.type = 'D' THEN bs.backup_finish_date END) AS ultimo_completo,
                   MAX(CASE WHEN bs.type = 'I' THEN bs.backup_finish_date END) AS ultimo_diferencial,
                   MAX(CASE WHEN bs.type = 'L' THEN bs.backup_finish_date END) AS ultimo_log,
                   DATEDIFF(HOUR, MAX(CASE WHEN bs.type = 'D' THEN bs.backup_finish_date END),
                            SYSDATETIME()) AS horas_desde_completo
            FROM sys.databases d
            LEFT JOIN msdb.dbo.backupset bs
                   ON bs.database_name COLLATE DATABASE_DEFAULT
                    = d.name           COLLATE DATABASE_DEFAULT
            WHERE d.database_id <> 2
            GROUP BY d.name, d.recovery_model_desc
            ORDER BY d.name
            """;

    private static final String SQL_JOBS = """
            SELECT TOP (25)
                   j.name      AS nombre_job,
                   h.step_name AS paso,
                   msdb.dbo.agent_datetime(h.run_date, h.run_time) AS fecha_ejecucion,
                   CASE h.run_status WHEN 0 THEN N'Fallido'
                                     WHEN 1 THEN N'Exitoso'
                                     WHEN 2 THEN N'Reintento'
                                     WHEN 3 THEN N'Cancelado'
                                     ELSE N'En progreso' END AS estado,
                   h.message   AS mensaje
            FROM msdb.dbo.sysjobhistory h
            JOIN msdb.dbo.sysjobs j ON j.job_id = h.job_id
            WHERE j.name LIKE N'%backup%' OR j.name LIKE N'%respaldo%'
            ORDER BY h.run_date DESC, h.run_time DESC
            """;

    public List<RegistroRespaldo> historial(Integer cantidad, String baseDatos) {
        int limite = limitar(cantidad, 50, 200);
        RowMapper<RegistroRespaldo> mapeador = (rs, fila) -> new RegistroRespaldo(
                rs.getString("base_datos"),
                aFechaHora(rs.getTimestamp("inicio")),
                aFechaHora(rs.getTimestamp("fin")),
                rs.getLong("duracion_seg"),
                rs.getString("tipo_respaldo"),
                rs.getString("modelo_recuperacion"),
                rs.getBigDecimal("tamano_mb"),
                rs.getBigDecimal("tamano_comprimido_mb"),
                rs.getBoolean("solo_copia"),
                rs.getString("usuario"),
                rs.getString("dispositivo"),
                "Exitoso");

        // Se indica el tipo explicito porque baseDatos puede ser nulo y el
        // driver de SQL Server necesita conocerlo para preparar la sentencia.
        return jdbc().query(SQL_HISTORIAL, sentencia -> {
            sentencia.setInt(1, limite);
            sentencia.setObject(2, baseDatos, Types.NVARCHAR);
            sentencia.setObject(3, baseDatos, Types.NVARCHAR);
        }, mapeador);
    }

    public List<UltimoRespaldoPorBase> ultimosRespaldos() {
        return consultar(SQL_ULTIMOS, (rs, fila) -> new UltimoRespaldoPorBase(
                rs.getString("base_datos"),
                rs.getString("modelo_recuperacion"),
                aFechaHora(rs.getTimestamp("ultimo_completo")),
                aFechaHora(rs.getTimestamp("ultimo_diferencial")),
                aFechaHora(rs.getTimestamp("ultimo_log")),
                rs.getObject("horas_desde_completo") == null ? null : rs.getInt("horas_desde_completo"),
                null, null));
    }

    public List<EjecucionJob> ejecucionesJobs() {
        return consultar(SQL_JOBS, (rs, fila) -> new EjecucionJob(
                rs.getString("nombre_job"),
                rs.getString("paso"),
                aFechaHora(rs.getTimestamp("fecha_ejecucion")),
                rs.getString("estado"),
                rs.getString("mensaje")));
    }

    public String baseDatosActual() {
        return jdbc().queryForObject("SELECT DB_NAME()", String.class);
    }

    private static LocalDateTime aFechaHora(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }
}
