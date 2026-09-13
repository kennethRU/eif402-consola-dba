package cr.ac.una.eif402.dbamonitor.conexion;

import cr.ac.una.eif402.dbamonitor.config.PropiedadesDba.PerfilConexion;
import cr.ac.una.eif402.dbamonitor.conexion.ConexionDtos.PerfilResumen;
import cr.ac.una.eif402.dbamonitor.conexion.ConexionDtos.ResultadoOperacion;
import org.springframework.stereotype.Service;

import java.util.List;

/** Logica de seleccion y verificacion de la instancia monitoreada. */
@Service
public class ConexionServicio {

    private final ProveedorConexiones proveedor;

    public ConexionServicio(ProveedorConexiones proveedor) {
        this.proveedor = proveedor;
    }

    public List<PerfilResumen> listarPerfiles() {
        String activo = proveedor.getPerfilActivo();
        return proveedor.listarPerfiles().stream()
                .map(perfil -> aResumen(perfil, perfil.getNombre().equalsIgnoreCase(activo)))
                .toList();
    }

    public PerfilResumen cambiarPerfil(String nombre) {
        proveedor.activarPerfil(nombre);
        return aResumen(proveedor.perfilActivoDetalle(), true);
    }

    public ResultadoOperacion probarConexionActiva() {
        PerfilConexion perfil = proveedor.perfilActivoDetalle();
        proveedor.verificarConexion(perfil);
        return new ResultadoOperacion(true,
                "Conexion establecida con " + perfil.resumen() + ".");
    }

    private PerfilResumen aResumen(PerfilConexion perfil, boolean activo) {
        String servidor = (perfil.getInstancia() == null || perfil.getInstancia().isBlank())
                ? perfil.getHost() + ":" + perfil.getPuerto()
                : perfil.getHost() + "\\" + perfil.getInstancia();
        return new PerfilResumen(perfil.getNombre(), perfil.getDescripcion(), servidor,
                perfil.getBaseDatos(), perfil.getUsuario(), activo);
    }
}
