package cr.ac.una.eif402.dbamonitor.common;

import java.time.LocalDateTime;

/** Cuerpo uniforme de error devuelto por la API. */
public record RespuestaError(
        LocalDateTime marcaTiempo,
        int codigo,
        String mensaje,
        String sugerencia,
        String ruta
) {
    public static RespuestaError de(int codigo, String mensaje, String sugerencia, String ruta) {
        return new RespuestaError(LocalDateTime.now(), codigo, mensaje, sugerencia, ruta);
    }
}
