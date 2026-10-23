package cr.ac.una.eif402.dbamonitor.mantenimiento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Objetos de transferencia del Modulo 6: mantenimiento preventivo. */
public final class MantenimientoDtos {

    private MantenimientoDtos() { }

    public record EstadisticaDesactualizada(
            String esquema,
            String tabla,
            String estadistica,
            LocalDateTime ultimaActualizacion,
            long filas,
            long filasMuestreadas,
            long modificacionesPendientes,
            Integer diasSinActualizar,
            String recomendacion
    ) { }

    public record IndiceFragmentado(
            String esquema,
            String tabla,
            String indice,
            String tipoIndice,
            BigDecimal fragmentacionPorcentaje,
            long paginas,
            String recomendacion
    ) { }

    public record ObjetoInvalido(
            String esquema,
            String objeto,
            String tipoObjeto,
            Integer numeroError,
            String mensajeError,
            LocalDateTime fechaDeteccion
    ) { }

    public record RegistroBitacora(
            LocalDateTime fechaEjecucion,
            String tipoOperacion,
            String objetoAfectado,
            String resultado,
            String mensaje,
            Integer duracionMs,
            String usuarioSolicitante
    ) { }

    /** Peticion para recalcular estadisticas. */
    public record SolicitudEstadisticas(String esquema, String tabla, boolean muestreoCompleto) { }

    /** Peticion para recompilar objetos. Sin objeto, recompila los pendientes. */
    public record SolicitudRecompilacion(String esquema, String objeto) { }

    /** Confirmacion devuelta al usuario tras ejecutar una accion. */
    public record ResultadoOperacion(
            boolean exitoso,
            String operacion,
            String objetoAfectado,
            String mensaje,
            Integer duracionMs,
            List<String> detalle
    ) { }

    public record ResumenModuloMantenimiento(
            List<EstadisticaDesactualizada> estadisticas,
            List<IndiceFragmentado> indices,
            List<ObjetoInvalido> objetosInvalidos,
            List<RegistroBitacora> bitacora,
            String diagnostico,
            List<String> advertencias
    ) { }
}
