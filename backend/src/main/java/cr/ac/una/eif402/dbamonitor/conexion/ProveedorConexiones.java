package cr.ac.una.eif402.dbamonitor.conexion;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.eif402.dbamonitor.common.ExcepcionSgbd;
import cr.ac.una.eif402.dbamonitor.config.PropiedadesDba;
import cr.ac.una.eif402.dbamonitor.config.PropiedadesDba.PerfilConexion;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra las conexiones hacia las instancias de SQL Server.
 *
 * Cada perfil declarado en la configuracion obtiene su propio pool de
 * conexiones, creado bajo demanda. El perfil activo puede cambiarse en
 * tiempo de ejecucion desde la interfaz, sin recompilar ni reiniciar.
 */
@Component
public class ProveedorConexiones {

    private static final Logger LOG = LoggerFactory.getLogger(ProveedorConexiones.class);

    private final PropiedadesDba propiedades;
    private final Map<String, HikariDataSource> poolesPorPerfil = new ConcurrentHashMap<>();
    private volatile String perfilActivo;

    public ProveedorConexiones(PropiedadesDba propiedades) {
        this.propiedades = propiedades;
        if (propiedades.getConexiones().isEmpty()) {
            throw new IllegalStateException(
                    "No hay perfiles de conexion definidos en la propiedad dba.conexiones.");
        }
        this.perfilActivo = propiedades.getPerfilInicial() != null
                ? propiedades.getPerfilInicial()
                : propiedades.getConexiones().get(0).getNombre();
    }

    public List<PerfilConexion> listarPerfiles() {
        return propiedades.getConexiones();
    }

    public String getPerfilActivo() {
        return perfilActivo;
    }

    public PerfilConexion perfilActivoDetalle() {
        return buscarPerfil(perfilActivo);
    }

    /** Cambia la instancia monitoreada y verifica que responda. */
    public synchronized void activarPerfil(String nombre) {
        PerfilConexion perfil = buscarPerfil(nombre);
        verificarConexion(perfil);
        this.perfilActivo = perfil.getNombre();
        LOG.info("Perfil de conexion activo: {} -> {}", perfil.getNombre(), perfil.resumen());
    }

    /** JdbcTemplate apuntando a la instancia actualmente seleccionada. */
    public JdbcTemplate plantilla() {
        return new JdbcTemplate(obtenerPool(perfilActivoDetalle()));
    }

    /** Ejecuta una prueba de conectividad sin cambiar el perfil activo. */
    public void verificarConexion(PerfilConexion perfil) {
        try {
            new JdbcTemplate(obtenerPool(perfil)).queryForObject("SELECT 1", Integer.class);
        } catch (Exception ex) {
            cerrarPool(perfil.getNombre());
            throw new ExcepcionSgbd(
                    "No fue posible conectar con el perfil '" + perfil.getNombre() + "'.",
                    "Verifique host, puerto, nombre de la base de datos y credenciales. Detalle: "
                            + mensajeRaiz(ex), ex);
        }
    }

    private HikariDataSource obtenerPool(PerfilConexion perfil) {
        return poolesPorPerfil.computeIfAbsent(perfil.getNombre(), nombre -> {
            HikariConfig configuracion = new HikariConfig();
            configuracion.setJdbcUrl(perfil.construirUrlJdbc());
            configuracion.setUsername(perfil.getUsuario());
            configuracion.setPassword(perfil.getContrasena());
            configuracion.setPoolName("pool-" + nombre);
            configuracion.setMaximumPoolSize(5);
            configuracion.setMinimumIdle(1);
            configuracion.setConnectionTimeout(10_000);
            configuracion.setValidationTimeout(5_000);
            configuracion.setReadOnly(false);
            return new HikariDataSource(configuracion);
        });
    }

    private PerfilConexion buscarPerfil(String nombre) {
        return propiedades.getConexiones().stream()
                .filter(perfil -> perfil.getNombre().equalsIgnoreCase(nombre))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe un perfil de conexion llamado '" + nombre + "'."));
    }

    private void cerrarPool(String nombre) {
        HikariDataSource pool = poolesPorPerfil.remove(nombre);
        if (pool != null) {
            pool.close();
        }
    }

    private String mensajeRaiz(Throwable ex) {
        Throwable actual = ex;
        while (actual.getCause() != null) {
            actual = actual.getCause();
        }
        return actual.getMessage();
    }

    @PreDestroy
    public void cerrarTodo() {
        poolesPorPerfil.values().forEach(HikariDataSource::close);
        poolesPorPerfil.clear();
    }
}
