package cr.ac.una.eif402.dbamonitor.common;

import cr.ac.una.eif402.dbamonitor.conexion.ProveedorConexiones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Base para los repositorios de los seis modulos.
 *
 * Centraliza el acceso al JdbcTemplate del perfil activo y ofrece
 * ejecucion tolerante a fallos para aquellas metricas que pueden no estar
 * disponibles segun la edicion de SQL Server o los permisos del usuario.
 */
public abstract class RepositorioAdministrativo {

    private static final Logger LOG = LoggerFactory.getLogger(RepositorioAdministrativo.class);

    protected final ProveedorConexiones proveedor;

    protected RepositorioAdministrativo(ProveedorConexiones proveedor) {
        this.proveedor = proveedor;
    }

    /** JdbcTemplate hacia la instancia seleccionada por el usuario. */
    protected JdbcTemplate jdbc() {
        return proveedor.plantilla();
    }

    protected <T> List<T> consultar(String sql, RowMapper<T> mapeador, Object... parametros) {
        return jdbc().query(sql, mapeador, parametros);
    }

    protected <T> Optional<T> consultarUno(String sql, RowMapper<T> mapeador, Object... parametros) {
        return jdbc().query(sql, mapeador, parametros).stream().findFirst();
    }

    /**
     * Ejecuta una consulta cuya metrica puede no existir en la edicion
     * instalada. Si falla, se registra y se devuelve el valor alterno en
     * lugar de interrumpir todo el modulo.
     */
    protected <T> T consultarOpcional(String descripcion, Supplier<T> consulta, T valorAlterno) {
        try {
            return consulta.get();
        } catch (Exception ex) {
            LOG.warn("Metrica no disponible [{}]: {}", descripcion, ex.getMessage());
            return valorAlterno;
        }
    }

    /**
     * Valida que un valor recibido del cliente pertenezca a una lista
     * blanca. Se usa para columnas de ordenamiento, que no pueden
     * parametrizarse en SQL y son vector de inyeccion.
     */
    protected String validarValorPermitido(String valor, List<String> permitidos, String predeterminado) {
        if (valor == null || valor.isBlank()) {
            return predeterminado;
        }
        return permitidos.stream()
                .filter(permitido -> permitido.equalsIgnoreCase(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Valor no permitido: '" + valor + "'. Opciones validas: " + permitidos));
    }

    /** Limita el tamano de los TOP para evitar extracciones innecesarias. */
    protected int limitar(Integer solicitado, int predeterminado, int maximo) {
        if (solicitado == null || solicitado <= 0) {
            return predeterminado;
        }
        return Math.min(solicitado, maximo);
    }
}
