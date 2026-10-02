package cr.ac.una.eif402.dbamonitor.rendimiento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Objetos de transferencia del Modulo 2: monitoreo de rendimiento. */
public final class RendimientoDtos {

    private RendimientoDtos() { }

    public record ConsultaCostosa(
            String baseDatos,
            long ejecuciones,
            BigDecimal cpuTotalMs,
            BigDecimal cpuPromedioMs,
            BigDecimal duracionTotalMs,
            BigDecimal duracionPromedioMs,
            long lecturasLogicasTotal,
            long lecturasLogicasPromedio,
            long escriturasTotal,
            LocalDateTime ultimaEjecucion,
            String textoConsulta
    ) { }

    public record ResumenEjecucion(
            long consultasEnCache,
            long ejecucionesTotales,
            BigDecimal duracionAcumuladaMs,
            BigDecimal duracionPromedioMs,
            BigDecimal cpuAcumuladoMs
    ) { }

    public record SesionActiva(
            int idSesion,
            String usuario,
            String equipoCliente,
            String aplicacion,
            String estado,
            String baseDatos,
            long cpuMs,
            long memoriaKb,
            long lecturas,
            long escrituras,
            LocalDateTime inicioSesion,
            LocalDateTime ultimaSolicitud,
            String direccionCliente,
            Integer sesionBloqueadora,
            String tipoEspera,
            Long tiempoEsperaMs,
            String comandoActual
    ) { }

    public record SesionBloqueada(
            int sesionBloqueada,
            int sesionBloqueadora,
            String tipoEspera,
            long tiempoEsperaMs,
            String recursoEsperado,
            String estado,
            String comando,
            String baseDatos,
            String usuarioBloqueador,
            String aplicacionBloqueadora,
            String consultaBloqueada,
            String consultaBloqueadora
    ) { }

    public record TipoEspera(
            String tipoEspera,
            long tareasEnEspera,
            BigDecimal esperaTotalSeg,
            BigDecimal esperaCpuSeg,
            BigDecimal porcentaje
    ) { }

    public record ResumenModuloRendimiento(
            ResumenEjecucion resumenEjecucion,
            List<ConsultaCostosa> consultasCostosas,
            List<SesionActiva> sesionesActivas,
            List<SesionBloqueada> sesionesBloqueadas,
            List<TipoEspera> principalesEsperas,
            String diagnosticoBloqueos,
            List<String> advertencias
    ) { }
}
