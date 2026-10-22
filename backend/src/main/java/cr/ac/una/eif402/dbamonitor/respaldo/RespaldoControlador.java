package cr.ac.una.eif402.dbamonitor.respaldo;

import cr.ac.una.eif402.dbamonitor.respaldo.RespaldoDtos.RegistroRespaldo;
import cr.ac.una.eif402.dbamonitor.respaldo.RespaldoDtos.ResumenModuloRespaldo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Modulo 4 - Respaldo y recuperacion. */
@RestController
@RequestMapping("/api/respaldos")
public class RespaldoControlador {

    private final RespaldoServicio servicio;
    private final RespaldoRepositorio repositorio;

    public RespaldoControlador(RespaldoServicio servicio, RespaldoRepositorio repositorio) {
        this.servicio = servicio;
        this.repositorio = repositorio;
    }

    @GetMapping("/resumen")
    public ResumenModuloRespaldo resumen(@RequestParam(required = false) Integer cantidad) {
        return servicio.obtenerResumen(cantidad);
    }

    @GetMapping("/historial")
    public List<RegistroRespaldo> historial(@RequestParam(required = false) Integer cantidad,
                                            @RequestParam(required = false) String baseDatos) {
        return repositorio.historial(cantidad, baseDatos);
    }
}
