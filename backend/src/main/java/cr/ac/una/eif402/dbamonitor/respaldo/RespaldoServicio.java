package cr.ac.una.eif402.dbamonitor.respaldo;

import cr.ac.una.eif402.dbamonitor.respaldo.RespaldoDtos.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Modulo 4. Convierte las fechas de respaldo en un nivel de riesgo
 * comprensible para el administrador (criterio "interpretacion
 * administrativa" de la rubrica).
 */
@Service
public class RespaldoServicio {

    private static final int HORAS_RIESGO_MEDIO = 24;
    private static final int HORAS_RIESGO_ALTO = 72;

    private final RespaldoRepositorio repositorio;

    public RespaldoServicio(RespaldoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public ResumenModuloRespaldo obtenerResumen(Integer cantidadHistorial) {
        List<String> advertencias = new ArrayList<>();
        String baseActual = repositorio.baseDatosActual();

        List<UltimoRespaldoPorBase> estados = repositorio.ultimosRespaldos().stream()
                .map(this::evaluarRiesgo)
                .toList();

        UltimoRespaldoPorBase actual = estados.stream()
                .filter(estado -> estado.baseDatos().equalsIgnoreCase(baseActual))
                .findFirst()
                .orElse(null);

        List<RegistroRespaldo> historial = repositorio.historial(cantidadHistorial, null);
        if (historial.isEmpty()) {
            advertencias.add("msdb.dbo.backupset no contiene registros. Ejecute al menos un respaldo "
                    + "completo para poder demostrar este modulo.");
        }

        List<EjecucionJob> jobs = consultarJobsToleranteAFallos(advertencias);

        advertencias.add("msdb.dbo.backupset almacena unicamente respaldos exitosos. El estado "
                + "'Fallido' proviene del historial de jobs del Agente SQL Server.");

        return new ResumenModuloRespaldo(actual, estados, historial, jobs,
                interpretar(actual), advertencias);
    }

    private List<EjecucionJob> consultarJobsToleranteAFallos(List<String> advertencias) {
        try {
            return repositorio.ejecucionesJobs();
        } catch (Exception ex) {
            advertencias.add("No fue posible leer el historial de jobs. En SQL Server Express el "
                    + "Agente SQL Server no esta disponible, por lo que los respaldos se ejecutan "
                    + "manualmente o con el Programador de tareas de Windows.");
            return List.of();
        }
    }

    private UltimoRespaldoPorBase evaluarRiesgo(UltimoRespaldoPorBase origen) {
        String nivel;
        String texto;
        Integer horas = origen.horasDesdeCompleto();

        if (origen.ultimoCompleto() == null) {
            nivel = "ALTO";
            texto = "No existe ningun respaldo completo registrado para esta base de datos.";
        } else if (horas != null && horas > HORAS_RIESGO_ALTO) {
            nivel = "ALTO";
            texto = "El ultimo respaldo completo tiene mas de 72 horas.";
        } else if (horas != null && horas > HORAS_RIESGO_MEDIO) {
            nivel = "MEDIO";
            texto = "El ultimo respaldo completo tiene mas de 24 horas.";
        } else {
            nivel = "BAJO";
            texto = "El respaldo completo esta al dia.";
        }

        if ("FULL".equalsIgnoreCase(origen.modeloRecuperacion()) && origen.ultimoLog() == null) {
            texto += " La base usa modelo FULL sin respaldos de log: el archivo de transacciones "
                    + "crecera indefinidamente.";
            if ("BAJO".equals(nivel)) {
                nivel = "MEDIO";
            }
        }

        return new UltimoRespaldoPorBase(origen.baseDatos(), origen.modeloRecuperacion(),
                origen.ultimoCompleto(), origen.ultimoDiferencial(), origen.ultimoLog(),
                horas, nivel, texto);
    }

    static String interpretar(UltimoRespaldoPorBase actual) {
        if (actual == null) {
            return "No hay informacion de respaldos para la base de datos monitoreada.";
        }
        return switch (actual.nivelRiesgo()) {
            case "ALTO" -> "Condicion de riesgo: " + actual.interpretacion();
            case "MEDIO" -> "Atencion: " + actual.interpretacion();
            default -> "Condicion adecuada. " + actual.interpretacion();
        };
    }
}
