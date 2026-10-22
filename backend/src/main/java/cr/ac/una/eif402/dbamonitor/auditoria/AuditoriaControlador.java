package cr.ac.una.eif402.dbamonitor.auditoria;

import cr.ac.una.eif402.dbamonitor.auditoria.AuditoriaDtos.Privilegio;
import cr.ac.una.eif402.dbamonitor.auditoria.AuditoriaDtos.ResumenModuloAuditoria;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Modulo 5 - Auditoria. */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaControlador {

    private final AuditoriaServicio servicio;
    private final AuditoriaRepositorio repositorio;

    public AuditoriaControlador(AuditoriaServicio servicio, AuditoriaRepositorio repositorio) {
        this.servicio = servicio;
        this.repositorio = repositorio;
    }

    @GetMapping("/resumen")
    public ResumenModuloAuditoria resumen() {
        return servicio.obtenerResumen();
    }

    @GetMapping("/privilegios")
    public List<Privilegio> privilegios() {
        return repositorio.privilegios();
    }
}
