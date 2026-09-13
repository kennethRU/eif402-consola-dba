package cr.ac.una.eif402.dbamonitor.conexion;

import cr.ac.una.eif402.dbamonitor.conexion.ConexionDtos.PerfilResumen;
import cr.ac.una.eif402.dbamonitor.conexion.ConexionDtos.ResultadoOperacion;
import cr.ac.una.eif402.dbamonitor.conexion.ConexionDtos.SolicitudCambioPerfil;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Endpoints para consultar y cambiar la instancia monitoreada. */
@RestController
@RequestMapping("/api/conexion")
public class ConexionControlador {

    private final ConexionServicio servicio;

    public ConexionControlador(ConexionServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/perfiles")
    public List<PerfilResumen> listarPerfiles() {
        return servicio.listarPerfiles();
    }

    @PostMapping("/perfil-activo")
    public PerfilResumen cambiarPerfil(@RequestBody SolicitudCambioPerfil solicitud) {
        return servicio.cambiarPerfil(solicitud.nombre());
    }

    @GetMapping("/prueba")
    public ResultadoOperacion probar() {
        return servicio.probarConexionActiva();
    }
}
