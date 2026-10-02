package cr.ac.una.eif402.dbamonitor.instancia;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Objetos de transferencia del Modulo 1: estado general de la instancia. */
public final class InstanciaDtos {

    private InstanciaDtos() { }

    public record EstadoInstancia(
            String nombreServidor,
            String nombreCompletoInstancia,
            String nombreInstancia,
            String nombreServicio,
            String edicion,
            String versionProducto,
            String nivelProducto,
            String descripcionVersion,
            String intercalacion,
            String estado,
            LocalDateTime fechaInicio,
            long segundosActividad,
            String tiempoActividadLegible,
            int cantidadCpu,
            BigDecimal memoriaFisicaMb,
            BigDecimal memoriaObjetivoMb,
            BigDecimal memoriaUtilizadaMb,
            BigDecimal porcentajeMemoria
    ) { }

    public record EstadoServicio(
            String nombreServicio,
            String estado,
            String tipoInicio,
            Integer idProceso,
            LocalDateTime ultimoArranque,
            String cuentaServicio
    ) { }

    public record BaseDatosAdministrada(
            int idBaseDatos,
            String nombre,
            String estado,
            String modeloRecuperacion,
            int nivelCompatibilidad,
            String intercalacion,
            LocalDateTime fechaCreacion,
            boolean soloLectura,
            BigDecimal tamanoTotalMb
    ) { }

    public record ComponenteMemoria(String componente, BigDecimal memoriaMb) { }

    /** Respuesta agregada del modulo. */
    public record ResumenModuloInstancia(
            EstadoInstancia instancia,
            List<EstadoServicio> servicios,
            List<BaseDatosAdministrada> basesDatos,
            List<ComponenteMemoria> distribucionMemoria,
            List<String> advertencias
    ) { }
}
