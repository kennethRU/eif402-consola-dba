package cr.ac.una.eif402.dbamonitor.mantenimiento;

import cr.ac.una.eif402.dbamonitor.common.RepositorioAdministrativo;
import cr.ac.una.eif402.dbamonitor.conexion.ProveedorConexiones;
import cr.ac.una.eif402.dbamonitor.mantenimiento.MantenimientoDtos.*;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Consultas y operaciones del Modulo 6.
 *
 * Las acciones que modifican el estado del SGBD se ejecutan SIEMPRE a
 * traves de procedimientos almacenados con parametros, nunca concatenando
 * SQL dinamico en Java.
 */
@Repository
public class MantenimientoRepositorio extends RepositorioAdministrativo {

    public MantenimientoRepositorio(ProveedorConexiones proveedor) {
        super(proveedor);
    }

    private static final String SQL_ESTADISTICAS = """
            SELECT TOP (?)
                   SCHEMA_NAME(o.schema_id) AS esquema,
                   o.name                   AS tabla,
                   s.name                   AS estadistica,
                   sp.last_updated          AS ultima_actualizacion,
                   sp.rows                  AS filas,
                   sp.rows_sampled          AS filas_muestreadas,
                   sp.modification_counter  AS modificaciones_pendientes,
                   DATEDIFF(DAY, sp.last_updated, SYSDATETIME()) AS dias_sin_actualizar
            FROM sys.stats s
            JOIN sys.objects o ON o.object_id = s.object_id
            CROSS APPLY sys.dm_db_stats_properties(s.object_id, s.stats_id) sp
            WHERE o.is_ms_shipped = 0 AND o.type = 'U'
            ORDER BY sp.modification_counter DESC, sp.last_updated ASC
            """;

    private static final String SQL_INDICES = """
            SELECT TOP (?)
                   SCHEMA_NAME(o.schema_id) AS esquema,
                   o.name                   AS tabla,
                   i.name                   AS indice,
                   i.type_desc              AS tipo_indice,
                   CAST(ips.avg_fragmentation_in_percent AS DECIMAL(5,2)) AS fragmentacion_porcentaje,
                   ips.page_count           AS paginas
            FROM sys.dm_db_index_physical_stats(DB_ID(), NULL, NULL, NULL, 'LIMITED') ips
            JOIN sys.indexes i ON i.object_id = ips.object_id AND i.index_id = ips.index_id
            JOIN sys.objects o ON o.object_id = ips.object_id
            WHERE ips.page_count > 100 AND i.name IS NOT NULL AND o.is_ms_shipped = 0
            ORDER BY ips.avg_fragmentation_in_percent DESC
            """;

    private static final String SQL_OBJETOS_INVALIDOS = """
            SELECT nombre_esquema, nombre_objeto, tipo_objeto,
                   numero_error, mensaje_error, fecha_deteccion
            FROM dba.objeto_invalido
            WHERE corregido = 0
            ORDER BY nombre_esquema, nombre_objeto
            """;

    private static final String SQL_BITACORA = """
            SELECT TOP (?)
                   fecha_ejecucion, tipo_operacion, objeto_afectado,
                   resultado, mensaje, duracion_ms, usuario_solicitante
            FROM dba.bitacora_mantenimiento
            ORDER BY fecha_ejecucion DESC
            """;

    public List<EstadisticaDesactualizada> estadisticas(Integer cantidad) {
        return consultar(SQL_ESTADISTICAS, (rs, fila) -> new EstadisticaDesactualizada(
                rs.getString("esquema"),
                rs.getString("tabla"),
                rs.getString("estadistica"),
                aFechaHora(rs.getTimestamp("ultima_actualizacion")),
                rs.getLong("filas"),
                rs.getLong("filas_muestreadas"),
                rs.getLong("modificaciones_pendientes"),
                rs.getObject("dias_sin_actualizar") == null ? null : rs.getInt("dias_sin_actualizar"),
                null), limitar(cantidad, 30, 200));
    }

    public List<IndiceFragmentado> indices(Integer cantidad) {
        return consultar(SQL_INDICES, (rs, fila) -> new IndiceFragmentado(
                rs.getString("esquema"),
                rs.getString("tabla"),
                rs.getString("indice"),
                rs.getString("tipo_indice"),
                rs.getBigDecimal("fragmentacion_porcentaje"),
                rs.getLong("paginas"),
                null), limitar(cantidad, 30, 200));
    }

    public List<ObjetoInvalido> objetosInvalidos() {
        return consultar(SQL_OBJETOS_INVALIDOS, (rs, fila) -> new ObjetoInvalido(
                rs.getString("nombre_esquema"),
                rs.getString("nombre_objeto"),
                rs.getString("tipo_objeto"),
                rs.getObject("numero_error") == null ? null : rs.getInt("numero_error"),
                rs.getString("mensaje_error"),
                aFechaHora(rs.getTimestamp("fecha_deteccion"))));
    }

    public List<RegistroBitacora> bitacora(Integer cantidad) {
        return consultar(SQL_BITACORA, (rs, fila) -> new RegistroBitacora(
                aFechaHora(rs.getTimestamp("fecha_ejecucion")),
                rs.getString("tipo_operacion"),
                rs.getString("objeto_afectado"),
                rs.getString("resultado"),
                rs.getString("mensaje"),
                rs.getObject("duracion_ms") == null ? null : rs.getInt("duracion_ms"),
                rs.getString("usuario_solicitante")), limitar(cantidad, 50, 200));
    }

    /** Ejecuta dba.usp_actualizar_estadisticas. */
    public Map<String, Object> actualizarEstadisticas(String esquema, String tabla, boolean completo) {
        String sql = "EXEC dba.usp_actualizar_estadisticas @nombre_esquema = ?, "
                + "@nombre_tabla = ?, @muestreo_completo = ?";
        List<Map<String, Object>> filas = jdbc().query(sql, sentencia -> {
            sentencia.setObject(1, esquema, Types.NVARCHAR);
            sentencia.setObject(2, tabla, Types.NVARCHAR);
            sentencia.setBoolean(3, completo);
        }, new ColumnMapRowMapper());
        return filas.isEmpty() ? Map.of() : filas.get(0);
    }

    /** Ejecuta dba.usp_validar_modulos y devuelve los objetos con problemas. */
    public List<ObjetoInvalido> validarModulos() {
        return jdbc().query("EXEC dba.usp_validar_modulos", (rs, fila) -> new ObjetoInvalido(
                rs.getString("nombre_esquema"),
                rs.getString("nombre_objeto"),
                rs.getString("tipo_objeto"),
                rs.getObject("numero_error") == null ? null : rs.getInt("numero_error"),
                rs.getString("mensaje_error"),
                aFechaHora(rs.getTimestamp("fecha_deteccion"))));
    }

    /** Ejecuta dba.usp_recompilar_objeto. */
    public List<Map<String, Object>> recompilar(String esquema, String objeto) {
        String sql = "EXEC dba.usp_recompilar_objeto @nombre_esquema = ?, @nombre_objeto = ?";
        return jdbc().query(sql, sentencia -> {
            sentencia.setObject(1, esquema, Types.NVARCHAR);
            sentencia.setObject(2, objeto, Types.NVARCHAR);
        }, new ColumnMapRowMapper());
    }

    private static LocalDateTime aFechaHora(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }
}
