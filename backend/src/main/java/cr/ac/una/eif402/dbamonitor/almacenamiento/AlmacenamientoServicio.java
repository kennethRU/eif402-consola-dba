package cr.ac.una.eif402.dbamonitor.almacenamiento;

import cr.ac.una.eif402.dbamonitor.almacenamiento.AlmacenamientoDtos.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modulo 3. Clasifica el nivel de alerta por filegroup y resuelve la
 * fuente del historico de crecimiento.
 */
@Service
public class AlmacenamientoServicio {

    private static final BigDecimal UMBRAL_CRITICO = BigDecimal.valueOf(90);
    private static final BigDecimal UMBRAL_ADVERTENCIA = BigDecimal.valueOf(75);

    private final AlmacenamientoRepositorio repositorio;

    public AlmacenamientoServicio(AlmacenamientoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public ResumenModuloAlmacenamiento obtenerResumen(Integer cantidadObjetos) {
        List<String> advertencias = new ArrayList<>();

        TamanoBaseDatos tamano = repositorio.tamanoBaseDatos().orElse(null);
        List<EspacioFilegroup> filegroups = repositorio.filegroups().stream()
                .map(this::clasificar)
                .toList();
        List<ArchivoDatos> archivos = repositorio.archivos();
        List<ObjetoVoluminoso> objetos = repositorio.objetosVoluminosos(cantidadObjetos);

        // Fuente primaria: bitacora propia. Alterna: tamano de los respaldos.
        List<PuntoCrecimiento> crecimiento = repositorio.crecimientoDesdeSnapshots();
        String origen = "Bitacora de la herramienta (dba.snapshot_almacenamiento)";

        if (crecimiento.size() < 2) {
            List<PuntoCrecimiento> alterno = repositorio.crecimientoDesdeRespaldos();
            if (alterno.size() >= 2) {
                crecimiento = alterno;
                origen = "Tamano de los respaldos completos (msdb.dbo.backupset)";
                advertencias.add("Aun no hay suficientes capturas propias. Se muestra el crecimiento "
                        + "inferido del tamano de los respaldos completos.");
            } else {
                advertencias.add("SQL Server no conserva historico de tamano. Registre capturas con "
                        + "el boton 'Registrar captura' o programe un job diario que ejecute "
                        + "dba.usp_registrar_snapshot_almacenamiento.");
            }
        }

        return new ResumenModuloAlmacenamiento(tamano, filegroups, archivos, objetos,
                crecimiento, origen, interpretarEspacio(filegroups), advertencias);
    }

    public Map<String, Object> registrarCaptura() {
        return repositorio.registrarSnapshot();
    }

    private EspacioFilegroup clasificar(EspacioFilegroup origen) {
        return new EspacioFilegroup(origen.filegroup(), origen.tipo(), origen.esPredeterminado(),
                origen.cantidadArchivos(), origen.tamanoMb(), origen.usadoMb(), origen.libreMb(),
                origen.porcentajeUso(), nivelAlerta(origen.porcentajeUso()));
    }

    static String nivelAlerta(BigDecimal porcentajeUso) {
        if (porcentajeUso == null) {
            return "DESCONOCIDO";
        }
        if (porcentajeUso.compareTo(UMBRAL_CRITICO) >= 0) {
            return "CRITICO";
        }
        if (porcentajeUso.compareTo(UMBRAL_ADVERTENCIA) >= 0) {
            return "ADVERTENCIA";
        }
        return "NORMAL";
    }

    static String interpretarEspacio(List<EspacioFilegroup> filegroups) {
        List<String> criticos = filegroups.stream()
                .filter(fg -> "CRITICO".equals(fg.nivelAlerta()))
                .map(EspacioFilegroup::filegroup)
                .toList();
        if (!criticos.isEmpty()) {
            return "Espacio critico en: " + String.join(", ", criticos)
                    + ". Amplie los archivos o libere espacio antes de que la base de datos se detenga.";
        }
        boolean hayAdvertencia = filegroups.stream()
                .anyMatch(fg -> "ADVERTENCIA".equals(fg.nivelAlerta()));
        if (hayAdvertencia) {
            return "Uno o mas filegroups superan el 75 % de uso. Planifique la ampliacion.";
        }
        return "El uso del espacio se mantiene dentro de los rangos esperados.";
    }
}
