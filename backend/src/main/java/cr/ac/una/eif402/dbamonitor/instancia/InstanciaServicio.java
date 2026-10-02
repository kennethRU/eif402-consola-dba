package cr.ac.una.eif402.dbamonitor.instancia;

import cr.ac.una.eif402.dbamonitor.common.ExcepcionSgbd;
import cr.ac.una.eif402.dbamonitor.instancia.InstanciaDtos.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Modulo 1. Interpreta los datos crudos de la instancia: calcula el tiempo
 * de actividad legible, el porcentaje de memoria y genera advertencias
 * cuando alguna metrica no esta disponible.
 */
@Service
public class InstanciaServicio {

    private final InstanciaRepositorio repositorio;

    public InstanciaServicio(InstanciaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public ResumenModuloInstancia obtenerResumen() {
        List<String> advertencias = new ArrayList<>();

        EstadoInstancia base = repositorio.obtenerEstado()
                .orElseThrow(() -> new ExcepcionSgbd(
                        "La instancia no devolvio informacion de estado.",
                        "Verifique que el usuario tenga el permiso VIEW SERVER STATE."));

        EstadoInstancia instancia = enriquecer(base);

        List<EstadoServicio> servicios = repositorio.listarServicios();
        if (servicios.isEmpty()) {
            advertencias.add("sys.dm_server_services no devolvio filas. En algunas ediciones o "
                    + "sin permiso VIEW SERVER STATE no esta disponible; el estado de la instancia "
                    + "se infiere de la respuesta a la consulta administrativa.");
        }

        List<BaseDatosAdministrada> basesDatos = repositorio.listarBasesDatos();
        List<ComponenteMemoria> memoria = repositorio.distribucionMemoria();

        if (instancia.memoriaObjetivoMb() == null) {
            advertencias.add("La instancia no reporta memoria objetivo. Se muestra unicamente "
                    + "la memoria confirmada por el sistema operativo.");
        }

        return new ResumenModuloInstancia(instancia, servicios, basesDatos, memoria, advertencias);
    }

    /** Agrega los valores derivados que la consulta no calcula. */
    private EstadoInstancia enriquecer(EstadoInstancia origen) {
        return new EstadoInstancia(
                origen.nombreServidor(),
                origen.nombreCompletoInstancia(),
                origen.nombreInstancia(),
                origen.nombreServicio(),
                origen.edicion(),
                origen.versionProducto(),
                origen.nivelProducto(),
                origen.descripcionVersion(),
                origen.intercalacion(),
                origen.estado(),
                origen.fechaInicio(),
                origen.segundosActividad(),
                formatearTiempoActividad(origen.segundosActividad()),
                origen.cantidadCpu(),
                origen.memoriaFisicaMb(),
                origen.memoriaObjetivoMb(),
                origen.memoriaUtilizadaMb(),
                calcularPorcentajeMemoria(origen.memoriaUtilizadaMb(), origen.memoriaObjetivoMb()));
    }

    /** Convierte los segundos de actividad a un formato "Xd Yh Zm". */
    static String formatearTiempoActividad(long segundos) {
        Duration duracion = Duration.ofSeconds(segundos);
        long dias = duracion.toDays();
        long horas = duracion.toHoursPart();
        long minutos = duracion.toMinutesPart();
        if (dias > 0) {
            return String.format("%d d %d h %d min", dias, horas, minutos);
        }
        if (horas > 0) {
            return String.format("%d h %d min", horas, minutos);
        }
        return String.format("%d min", minutos);
    }

    static BigDecimal calcularPorcentajeMemoria(BigDecimal utilizada, BigDecimal objetivo) {
        if (utilizada == null || objetivo == null || objetivo.signum() == 0) {
            return null;
        }
        return utilizada.multiply(BigDecimal.valueOf(100))
                .divide(objetivo, 2, RoundingMode.HALF_UP);
    }
}
