package cr.ac.una.eif402.dbamonitor.almacenamiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Objetos de transferencia del Modulo 3: gestion del almacenamiento. */
public final class AlmacenamientoDtos {

    private AlmacenamientoDtos() { }

    public record EspacioFilegroup(
            String filegroup,
            String tipo,
            boolean esPredeterminado,
            int cantidadArchivos,
            BigDecimal tamanoMb,
            BigDecimal usadoMb,
            BigDecimal libreMb,
            BigDecimal porcentajeUso,
            String nivelAlerta
    ) { }

    public record ArchivoDatos(
            int idArchivo,
            String nombreLogico,
            String rutaFisica,
            String tipo,
            String filegroup,
            String estado,
            BigDecimal tamanoMb,
            BigDecimal usadoMb,
            BigDecimal libreMb,
            String tamanoMaximo,
            String crecimientoConfigurado
    ) { }

    public record TamanoBaseDatos(
            String baseDatos,
            BigDecimal tamanoTotalMb,
            BigDecimal datosMb,
            BigDecimal logMb,
            BigDecimal usadoMb
    ) { }

    public record ObjetoVoluminoso(
            String esquema,
            String objeto,
            BigDecimal reservadoMb,
            BigDecimal usadoMb,
            long filas,
            int cantidadIndices
    ) { }

    public record PuntoCrecimiento(LocalDate fecha, BigDecimal tamanoTotalMb, BigDecimal usadoMb) { }

    public record ResumenModuloAlmacenamiento(
            TamanoBaseDatos tamanoBaseDatos,
            List<EspacioFilegroup> filegroups,
            List<ArchivoDatos> archivos,
            List<ObjetoVoluminoso> objetosVoluminosos,
            List<PuntoCrecimiento> crecimiento,
            String origenCrecimiento,
            String diagnostico,
            List<String> advertencias
    ) { }
}
