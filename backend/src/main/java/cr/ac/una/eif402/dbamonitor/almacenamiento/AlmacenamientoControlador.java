package cr.ac.una.eif402.dbamonitor.almacenamiento;

import cr.ac.una.eif402.dbamonitor.almacenamiento.AlmacenamientoDtos.ResumenModuloAlmacenamiento;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Modulo 3 - Gestion del almacenamiento. */
@RestController
@RequestMapping("/api/almacenamiento")
public class AlmacenamientoControlador {

    private final AlmacenamientoServicio servicio;

    public AlmacenamientoControlador(AlmacenamientoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/resumen")
    public ResumenModuloAlmacenamiento resumen(@RequestParam(required = false) Integer cantidadObjetos) {
        return servicio.obtenerResumen(cantidadObjetos);
    }

    /** Registra un punto historico de tamano para el analisis de crecimiento. */
    @PostMapping("/capturas")
    public Map<String, Object> registrarCaptura() {
        return servicio.registrarCaptura();
    }
}
