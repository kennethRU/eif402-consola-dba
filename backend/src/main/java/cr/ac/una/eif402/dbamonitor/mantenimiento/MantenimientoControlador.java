package cr.ac.una.eif402.dbamonitor.mantenimiento;

import cr.ac.una.eif402.dbamonitor.mantenimiento.MantenimientoDtos.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Modulo 6 - Mantenimiento preventivo. */
@RestController
@RequestMapping("/api/mantenimiento")
public class MantenimientoControlador {

    private final MantenimientoServicio servicio;
    private final MantenimientoRepositorio repositorio;

    public MantenimientoControlador(MantenimientoServicio servicio,
                                    MantenimientoRepositorio repositorio) {
        this.servicio = servicio;
        this.repositorio = repositorio;
    }

    @GetMapping("/resumen")
    public ResumenModuloMantenimiento resumen(@RequestParam(required = false) Integer cantidad) {
        return servicio.obtenerResumen(cantidad);
    }

    @PostMapping("/estadisticas")
    public ResultadoOperacion actualizarEstadisticas(@RequestBody SolicitudEstadisticas solicitud) {
        return servicio.actualizarEstadisticas(solicitud);
    }

    @PostMapping("/validacion")
    public ResultadoOperacion validarObjetos() {
        return servicio.validarObjetos();
    }

    @PostMapping("/recompilacion")
    public ResultadoOperacion recompilar(@RequestBody SolicitudRecompilacion solicitud) {
        return servicio.recompilar(solicitud);
    }

    @GetMapping("/bitacora")
    public List<RegistroBitacora> bitacora(@RequestParam(required = false) Integer cantidad) {
        return repositorio.bitacora(cantidad);
    }
}
