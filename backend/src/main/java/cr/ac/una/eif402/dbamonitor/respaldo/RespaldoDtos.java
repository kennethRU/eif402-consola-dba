package cr.ac.una.eif402.dbamonitor.respaldo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Objetos de transferencia del Modulo 4: respaldo y recuperacion. */
public final class RespaldoDtos {

    private RespaldoDtos() { }

    public record RegistroRespaldo(
            String baseDatos,
            OffsetDateTime inicio,
            OffsetDateTime fin,
            long duracionSeg,
            String tipoRespaldo,
            String modeloRecuperacion,
            BigDecimal tamanoMb,
            BigDecimal tamanoComprimidoMb,
            boolean soloCopia,
            String usuario,
            String dispositivo,
            String estado
    ) { }

    public record UltimoRespaldoPorBase(
            String baseDatos,
            String modeloRecuperacion,
            OffsetDateTime ultimoCompleto,
            OffsetDateTime ultimoDiferencial,
            OffsetDateTime ultimoLog,
            Integer horasDesdeCompleto,
            String nivelRiesgo,
            String interpretacion
    ) { }

    public record EjecucionJob(
            String nombreJob,
            String paso,
            OffsetDateTime fechaEjecucion,
            String estado,
            String mensaje
    ) { }

    public record ResumenModuloRespaldo(
            UltimoRespaldoPorBase respaldoBaseActual,
            List<UltimoRespaldoPorBase> estadoPorBase,
            List<RegistroRespaldo> historial,
            List<EjecucionJob> ejecucionesJobs,
            String diagnostico,
            List<String> advertencias
    ) { }
}
