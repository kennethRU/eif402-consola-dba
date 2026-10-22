package cr.ac.una.eif402.dbamonitor.almacenamiento;

import cr.ac.una.eif402.dbamonitor.almacenamiento.AlmacenamientoDtos.*;
import cr.ac.una.eif402.dbamonitor.common.RepositorioAdministrativo;
import cr.ac.una.eif402.dbamonitor.conexion.ProveedorConexiones;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas administrativas del Modulo 3.
 * Fuentes: sys.database_files, sys.filegroups, FILEPROPERTY,
 * sys.dm_db_partition_stats, dba.snapshot_almacenamiento, msdb.dbo.backupset.
 */
@Repository
public class AlmacenamientoRepositorio extends RepositorioAdministrativo {

    public AlmacenamientoRepositorio(ProveedorConexiones proveedor) {
        super(proveedor);
    }

    private static final String SQL_FILEGROUPS = """
            SELECT fg.name        AS filegroup,
                   fg.type_desc   AS tipo,
                   fg.is_default  AS es_predeterminado,
                   COUNT(df.file_id) AS cantidad_archivos,
                   CAST(SUM(df.size) * 8.0 / 1024 AS DECIMAL(18,2)) AS tamano_mb,
                   CAST(SUM(CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT)) * 8.0 / 1024 AS DECIMAL(18,2)) AS usado_mb,
                   CAST((SUM(df.size) - SUM(CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT))) * 8.0 / 1024 AS DECIMAL(18,2)) AS libre_mb,
                   CAST(100.0 * SUM(CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT))
                        / NULLIF(SUM(df.size), 0) AS DECIMAL(5,2)) AS porcentaje_uso
            FROM sys.filegroups fg
            JOIN sys.database_files df ON df.data_space_id = fg.data_space_id
            GROUP BY fg.name, fg.type_desc, fg.is_default
            """;

    private static final String SQL_ARCHIVOS = """
            SELECT df.file_id        AS id_archivo,
                   df.name           AS nombre_logico,
                   df.physical_name  AS ruta_fisica,
                   df.type_desc      AS tipo,
                   fg.name           AS filegroup,
                   df.state_desc     AS estado,
                   CAST(df.size * 8.0 / 1024 AS DECIMAL(18,2)) AS tamano_mb,
                   CAST(CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT) * 8.0 / 1024 AS DECIMAL(18,2)) AS usado_mb,
                   CAST((df.size - CAST(FILEPROPERTY(df.name,'SpaceUsed') AS BIGINT)) * 8.0 / 1024 AS DECIMAL(18,2)) AS libre_mb,
                   CASE df.max_size WHEN -1 THEN N'Sin limite'
                                    WHEN 268435456 THEN N'2 TB'
                                    ELSE CONCAT(CAST(df.max_size * 8.0 / 1024 AS DECIMAL(18,2)), N' MB') END AS tamano_maximo,
                   CASE WHEN df.is_percent_growth = 1 THEN CONCAT(df.growth, N' %')
                        ELSE CONCAT(CAST(df.growth * 8.0 / 1024 AS DECIMAL(18,2)), N' MB') END AS crecimiento_configurado
            FROM sys.database_files df
            LEFT JOIN sys.filegroups fg ON fg.data_space_id = df.data_space_id
            ORDER BY df.type, df.file_id
            """;

    private static final String SQL_TAMANO_BD = """
            SELECT DB_NAME() AS base_datos,
                   CAST(SUM(size) * 8.0 / 1024 AS DECIMAL(18,2)) AS tamano_total_mb,
                   CAST(SUM(CASE WHEN type = 0 THEN size ELSE 0 END) * 8.0 / 1024 AS DECIMAL(18,2)) AS datos_mb,
                   CAST(SUM(CASE WHEN type = 1 THEN size ELSE 0 END) * 8.0 / 1024 AS DECIMAL(18,2)) AS log_mb,
                   CAST(SUM(CAST(FILEPROPERTY(name,'SpaceUsed') AS BIGINT)) * 8.0 / 1024 AS DECIMAL(18,2)) AS usado_mb
            FROM sys.database_files
            """;

    private static final String SQL_OBJETOS = """
            SELECT TOP (?)
                   sch.name AS esquema,
                   t.name   AS objeto,
                   CAST(SUM(ps.reserved_page_count) * 8.0 / 1024 AS DECIMAL(18,2)) AS reservado_mb,
                   CAST(SUM(ps.used_page_count)     * 8.0 / 1024 AS DECIMAL(18,2)) AS usado_mb,
                   SUM(CASE WHEN ps.index_id IN (0,1) THEN ps.row_count ELSE 0 END) AS filas,
                   (SELECT COUNT(*) FROM sys.indexes i WHERE i.object_id = t.object_id AND i.index_id > 0) AS cantidad_indices
            FROM sys.dm_db_partition_stats ps
            JOIN sys.tables  t   ON t.object_id   = ps.object_id
            JOIN sys.schemas sch ON sch.schema_id = t.schema_id
            WHERE t.is_ms_shipped = 0
            GROUP BY sch.name, t.name, t.object_id
            ORDER BY SUM(ps.reserved_page_count) DESC
            """;

    private static final String SQL_CRECIMIENTO_SNAPSHOT = """
            SELECT CAST(fecha_captura AS DATE)           AS fecha,
                   CAST(SUM(tamano_mb) AS DECIMAL(18,2)) AS tamano_total_mb,
                   CAST(SUM(usado_mb)  AS DECIMAL(18,2)) AS usado_mb
            FROM dba.snapshot_almacenamiento
            WHERE nombre_base_datos = DB_NAME()
            GROUP BY CAST(fecha_captura AS DATE)
            ORDER BY fecha
            """;

    private static final String SQL_CRECIMIENTO_RESPALDOS = """
            SELECT CAST(bs.backup_start_date AS DATE) AS fecha,
                   CAST(MAX(bs.backup_size) / 1024.0 / 1024 AS DECIMAL(18,2)) AS tamano_total_mb,
                   CAST(MAX(bs.backup_size) / 1024.0 / 1024 AS DECIMAL(18,2)) AS usado_mb
            FROM msdb.dbo.backupset bs
            WHERE bs.database_name COLLATE DATABASE_DEFAULT = DB_NAME() AND bs.type = 'D'
            GROUP BY CAST(bs.backup_start_date AS DATE)
            ORDER BY fecha
            """;

    public List<EspacioFilegroup> filegroups() {
        return consultar(SQL_FILEGROUPS, (rs, fila) -> new EspacioFilegroup(
                rs.getString("filegroup"),
                rs.getString("tipo"),
                rs.getBoolean("es_predeterminado"),
                rs.getInt("cantidad_archivos"),
                rs.getBigDecimal("tamano_mb"),
                rs.getBigDecimal("usado_mb"),
                rs.getBigDecimal("libre_mb"),
                rs.getBigDecimal("porcentaje_uso"),
                null));
    }

    public List<ArchivoDatos> archivos() {
        return consultar(SQL_ARCHIVOS, (rs, fila) -> new ArchivoDatos(
                rs.getInt("id_archivo"),
                rs.getString("nombre_logico"),
                rs.getString("ruta_fisica"),
                rs.getString("tipo"),
                rs.getString("filegroup"),
                rs.getString("estado"),
                rs.getBigDecimal("tamano_mb"),
                rs.getBigDecimal("usado_mb"),
                rs.getBigDecimal("libre_mb"),
                rs.getString("tamano_maximo"),
                rs.getString("crecimiento_configurado")));
    }

    public Optional<TamanoBaseDatos> tamanoBaseDatos() {
        return consultarUno(SQL_TAMANO_BD, (rs, fila) -> new TamanoBaseDatos(
                rs.getString("base_datos"),
                rs.getBigDecimal("tamano_total_mb"),
                rs.getBigDecimal("datos_mb"),
                rs.getBigDecimal("log_mb"),
                rs.getBigDecimal("usado_mb")));
    }

    public List<ObjetoVoluminoso> objetosVoluminosos(Integer cantidad) {
        return consultar(SQL_OBJETOS, (rs, fila) -> new ObjetoVoluminoso(
                rs.getString("esquema"),
                rs.getString("objeto"),
                rs.getBigDecimal("reservado_mb"),
                rs.getBigDecimal("usado_mb"),
                rs.getLong("filas"),
                rs.getInt("cantidad_indices")), limitar(cantidad, 20, 100));
    }

    public List<PuntoCrecimiento> crecimientoDesdeSnapshots() {
        return consultar(SQL_CRECIMIENTO_SNAPSHOT, this::mapearPunto);
    }

    public List<PuntoCrecimiento> crecimientoDesdeRespaldos() {
        return consultar(SQL_CRECIMIENTO_RESPALDOS, this::mapearPunto);
    }

    /** Registra un punto historico de tamano invocando el procedimiento almacenado. */
    public Map<String, Object> registrarSnapshot() {
        return jdbc().queryForMap("EXEC dba.usp_registrar_snapshot_almacenamiento");
    }

    private PuntoCrecimiento mapearPunto(java.sql.ResultSet rs, int fila) throws java.sql.SQLException {
        return new PuntoCrecimiento(
                rs.getDate("fecha").toLocalDate(),
                rs.getBigDecimal("tamano_total_mb"),
                rs.getBigDecimal("usado_mb"));
    }
}
