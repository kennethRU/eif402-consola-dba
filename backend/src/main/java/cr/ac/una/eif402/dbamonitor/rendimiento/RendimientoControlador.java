package cr.ac.una.eif402.dbamonitor.rendimiento;

import cr.ac.una.eif402.dbamonitor.rendimiento.RendimientoDtos.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Modulo 2 - Monitoreo de rendimiento. */
@RestController
@RequestMapping("/api/rendimiento")
public class RendimientoControlador {

    private final RendimientoServicio servicio;

    public RendimientoControlador(RendimientoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/resumen")
    public ResumenModuloRendimiento resumen(
            @RequestParam(required = false) Integer cantidad,
            @RequestParam(required = false, name = "ordenarPor") String ordenarPor) {
        return servicio.obtenerResumen(cantidad, ordenarPor);
    }

    @GetMapping("/consultas-costosas")
    public List<ConsultaCostosa> consultasCostosas(
            @RequestParam(required = false) Integer cantidad,
            @RequestParam(required = false) String ordenarPor) {
        return servicio.consultasCostosas(cantidad, ordenarPor);
    }

    @GetMapping("/sesiones")
    public List<SesionActiva> sesiones() {
        return servicio.sesionesActivas();
    }

    @GetMapping("/sesiones-bloqueadas")
    public List<SesionBloqueada> sesionesBloqueadas() {
        return servicio.sesionesBloqueadas();
    }
}
