package cr.ac.una.eif402.dbamonitor.mantenimiento;

import cr.ac.una.eif402.dbamonitor.common.ExcepcionSgbd;
import cr.ac.una.eif402.dbamonitor.mantenimiento.MantenimientoDtos.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Modulo 6. Ejecuta las acciones de mantenimiento y confirma su resultado.
 *
 * Los nombres de esquema y objeto recibidos del cliente se validan contra
 * un patron de identificador antes de pasarlos al procedimiento almacenado.
 */
@Service
public class MantenimientoServicio {

    private static final Pattern IDENTIFICADOR_VALIDO = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$#]{0,127}$");
    private static final BigDecimal FRAGMENTACION_REBUILD = BigDecimal.valueOf(30);
    private static final BigDecimal FRAGMENTACION_REORGANIZE = BigDecimal.valueOf(10);
    private static final long MODIFICACIONES_RELEVANTES = 500;

    private final MantenimientoRepositorio repositorio;

    public MantenimientoServicio(MantenimientoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public ResumenModuloMantenimiento obtenerResumen(Integer cantidad) {
        List<String> advertencias = new ArrayList<>();

        List<EstadisticaDesactualizada> estadisticas = repositorio.estadisticas(cantidad).stream()
                .map(this::recomendarEstadistica)
                .toList();

        List<IndiceFragmentado> indices;
        try {
            indices = repositorio.indices(cantidad).stream()
                    .map(this::recomendarIndice)
                    .toList();
        } catch (Exception ex) {
            indices = List.of();
            advertencias.add("No fue posible leer la fragmentacion de indices. "
                    + "sys.dm_db_index_physical_stats requiere permisos sobre los objetos consultados.");
        }

        List<ObjetoInvalido> invalidos = repositorio.objetosInvalidos();
        List<RegistroBitacora> bitacora = repositorio.bitacora(cantidad);

        if (invalidos.isEmpty() && bitacora.stream().noneMatch(r -> "VALIDACION".equals(r.tipoOperacion()))) {
            advertencias.add("Aun no se ha ejecutado la validacion de modulos. "
                    + "Use 'Validar objetos' para detectar objetos con errores de compilacion.");
        }

        return new ResumenModuloMantenimiento(estadisticas, indices, invalidos, bitacora,
                interpretar(estadisticas, indices, invalidos), advertencias);
    }

    /** Recalcula estadisticas y devuelve la confirmacion al usuario. */
    public ResultadoOperacion actualizarEstadisticas(SolicitudEstadisticas solicitud) {
        String esquema = normalizarIdentificador(solicitud.esquema(), "esquema");
        String tabla = normalizarIdentificador(solicitud.tabla(), "tabla");

        Map<String, Object> fila = repositorio.actualizarEstadisticas(
                esquema, tabla, solicitud.muestreoCompleto());

        boolean exitoso = "EXITOSO".equals(fila.get("resultado"));
        return new ResultadoOperacion(
                exitoso,
                "Recalculo de estadisticas",
                String.valueOf(fila.get("objeto_afectado")),
                String.valueOf(fila.get("mensaje")),
                fila.get("duracion_ms") == null ? null : ((Number) fila.get("duracion_ms")).intValue(),
                List.of("Comando ejecutado: " + fila.get("comando_ejecutado")));
    }

    /** Detecta objetos con errores de compilacion o dependencias rotas. */
    public ResultadoOperacion validarObjetos() {
        List<ObjetoInvalido> encontrados = repositorio.validarModulos();
        String mensaje = encontrados.isEmpty()
                ? "Todos los modulos SQL se validaron correctamente. No hay objetos invalidos."
                : "Se detectaron " + encontrados.size() + " objeto(s) con errores de compilacion.";
        List<String> detalle = encontrados.stream()
                .map(objeto -> objeto.esquema() + "." + objeto.objeto() + " -> " + objeto.mensajeError())
                .toList();
        return new ResultadoOperacion(true, "Validacion de objetos",
                "(todos los modulos SQL)", mensaje, null, detalle);
    }

    /** Recompila los objetos invalidos detectados. */
    public ResultadoOperacion recompilar(SolicitudRecompilacion solicitud) {
        String esquema = normalizarIdentificador(solicitud.esquema(), "esquema");
        String objeto = normalizarIdentificador(solicitud.objeto(), "objeto");

        List<Map<String, Object>> filas = repositorio.recompilar(esquema, objeto);

        if (filas.isEmpty()) {
            return new ResultadoOperacion(true, "Recompilacion", objeto == null ? "(pendientes)" : objeto,
                    "No habia objetos pendientes de recompilar.", null, List.of());
        }

        long fallidos = filas.stream()
                .filter(fila -> "FALLIDO".equals(fila.get("resultado")))
                .count();

        List<String> detalle = filas.stream()
                .map(fila -> fila.get("nombre_esquema") + "." + fila.get("nombre_objeto")
                        + " -> " + fila.get("resultado") + ": " + fila.get("mensaje"))
                .toList();

        return new ResultadoOperacion(
                fallidos == 0,
                "Recompilacion",
                objeto == null ? "(todos los pendientes)" : esquema + "." + objeto,
                String.format("Recompilados %d de %d objeto(s).", filas.size() - fallidos, filas.size()),
                null,
                detalle);
    }

    private EstadisticaDesactualizada recomendarEstadistica(EstadisticaDesactualizada origen) {
        String recomendacion;
        if (origen.modificacionesPendientes() >= MODIFICACIONES_RELEVANTES) {
            recomendacion = "Recalcular: hay " + origen.modificacionesPendientes()
                    + " modificaciones sin reflejar en la estadistica.";
        } else if (origen.diasSinActualizar() != null && origen.diasSinActualizar() > 30) {
            recomendacion = "Revisar: sin actualizar hace " + origen.diasSinActualizar() + " dias.";
        } else {
            recomendacion = "Sin accion";
        }
        return new EstadisticaDesactualizada(origen.esquema(), origen.tabla(), origen.estadistica(),
                origen.ultimaActualizacion(), origen.filas(), origen.filasMuestreadas(),
                origen.modificacionesPendientes(), origen.diasSinActualizar(), recomendacion);
    }

    private IndiceFragmentado recomendarIndice(IndiceFragmentado origen) {
        BigDecimal fragmentacion = origen.fragmentacionPorcentaje();
        String recomendacion;
        if (fragmentacion == null) {
            recomendacion = "Sin datos";
        } else if (fragmentacion.compareTo(FRAGMENTACION_REBUILD) >= 0) {
            recomendacion = "Reconstruir (REBUILD)";
        } else if (fragmentacion.compareTo(FRAGMENTACION_REORGANIZE) >= 0) {
            recomendacion = "Reorganizar (REORGANIZE)";
        } else {
            recomendacion = "Sin accion";
        }
        return new IndiceFragmentado(origen.esquema(), origen.tabla(), origen.indice(),
                origen.tipoIndice(), fragmentacion, origen.paginas(), recomendacion);
    }

    static String interpretar(List<EstadisticaDesactualizada> estadisticas,
                              List<IndiceFragmentado> indices,
                              List<ObjetoInvalido> invalidos) {
        List<String> puntos = new ArrayList<>();
        long estadisticasPendientes = estadisticas.stream()
                .filter(estadistica -> estadistica.recomendacion().startsWith("Recalcular"))
                .count();
        long indicesPendientes = indices.stream()
                .filter(indice -> indice.recomendacion().startsWith("Recon")
                        || indice.recomendacion().startsWith("Reorg"))
                .count();

        if (estadisticasPendientes > 0) {
            puntos.add(estadisticasPendientes + " estadistica(s) requieren recalculo");
        }
        if (indicesPendientes > 0) {
            puntos.add(indicesPendientes + " indice(s) con fragmentacion relevante");
        }
        if (!invalidos.isEmpty()) {
            puntos.add(invalidos.size() + " objeto(s) invalido(s) pendientes de recompilar");
        }
        return puntos.isEmpty()
                ? "No hay tareas de mantenimiento pendientes."
                : "Tareas pendientes: " + String.join("; ", puntos) + ".";
    }

    /**
     * Verifica que el identificador recibido sea un nombre valido de SQL
     * Server. Evita que un valor manipulado llegue al procedimiento.
     */
    private String normalizarIdentificador(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        if (!IDENTIFICADOR_VALIDO.matcher(limpio).matches()) {
            throw new ExcepcionSgbd(
                    "El nombre de " + campo + " '" + valor + "' no es un identificador valido.",
                    "Use solo letras, digitos y guion bajo, comenzando por letra o guion bajo.");
        }
        return limpio;
    }
}
