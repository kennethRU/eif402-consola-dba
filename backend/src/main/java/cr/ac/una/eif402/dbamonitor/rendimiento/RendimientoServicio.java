package cr.ac.una.eif402.dbamonitor.rendimiento;

import cr.ac.una.eif402.dbamonitor.rendimiento.RendimientoDtos.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Modulo 2. Ademas de recuperar las metricas, entrega una lectura
 * administrativa de los bloqueos y advierte sobre las limitaciones de las
 * estadisticas del cache de planes.
 */
@Service
public class RendimientoServicio {

    private static final int UMBRAL_ESPERA_CRITICA_MS = 5_000;

    private final RendimientoRepositorio repositorio;

    public RendimientoServicio(RendimientoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public ResumenModuloRendimiento obtenerResumen(Integer cantidadConsultas, String ordenarPor) {
        List<String> advertencias = new ArrayList<>();

        ResumenEjecucion resumen = repositorio.resumenEjecucion().orElse(null);
        List<ConsultaCostosa> costosas = repositorio.consultasCostosas(cantidadConsultas, ordenarPor);
        List<SesionActiva> sesiones = repositorio.sesionesActivas();
        List<SesionBloqueada> bloqueadas = repositorio.sesionesBloqueadas();
        List<TipoEspera> esperas = repositorio.principalesEsperas();

        if (costosas.isEmpty()) {
            advertencias.add("El cache de planes no contiene consultas. Los contadores se reinician "
                    + "al reiniciar la instancia o al ejecutar DBCC FREEPROCCACHE.");
        }

        return new ResumenModuloRendimiento(resumen, costosas, sesiones, bloqueadas, esperas,
                interpretarBloqueos(bloqueadas), advertencias);
    }

    /** Traduce el estado de bloqueos a una conclusion para el administrador. */
    static String interpretarBloqueos(List<SesionBloqueada> bloqueadas) {
        if (bloqueadas.isEmpty()) {
            return "No hay sesiones bloqueadas en este momento.";
        }
        long criticas = bloqueadas.stream()
                .filter(sesion -> sesion.tiempoEsperaMs() >= UMBRAL_ESPERA_CRITICA_MS)
                .count();
        if (criticas > 0) {
            return String.format(
                    "%d sesion(es) bloqueada(s), de las cuales %d superan los 5 segundos de espera. "
                            + "Revise la sesion bloqueadora antes de que se propague el bloqueo.",
                    bloqueadas.size(), criticas);
        }
        return String.format("%d sesion(es) bloqueada(s) con esperas breves. "
                + "Es un comportamiento normal bajo concurrencia.", bloqueadas.size());
    }

    public List<ConsultaCostosa> consultasCostosas(Integer cantidad, String ordenarPor) {
        return repositorio.consultasCostosas(cantidad, ordenarPor);
    }

    public List<SesionActiva> sesionesActivas() {
        return repositorio.sesionesActivas();
    }

    public List<SesionBloqueada> sesionesBloqueadas() {
        return repositorio.sesionesBloqueadas();
    }
}
