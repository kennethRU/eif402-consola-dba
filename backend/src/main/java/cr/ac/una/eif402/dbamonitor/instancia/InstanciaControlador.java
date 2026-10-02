package cr.ac.una.eif402.dbamonitor.instancia;

import cr.ac.una.eif402.dbamonitor.instancia.InstanciaDtos.BaseDatosAdministrada;
import cr.ac.una.eif402.dbamonitor.instancia.InstanciaDtos.ResumenModuloInstancia;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Modulo 1 - Estado general de la instancia del SGBD. */
@RestController
@RequestMapping("/api/instancia")
public class InstanciaControlador {

    private final InstanciaServicio servicio;
    private final InstanciaRepositorio repositorio;

    public InstanciaControlador(InstanciaServicio servicio, InstanciaRepositorio repositorio) {
        this.servicio = servicio;
        this.repositorio = repositorio;
    }

    @GetMapping("/estado")
    public ResumenModuloInstancia obtenerEstado() {
        return servicio.obtenerResumen();
    }

    @GetMapping("/bases-datos")
    public List<BaseDatosAdministrada> listarBasesDatos() {
        return repositorio.listarBasesDatos();
    }
}
