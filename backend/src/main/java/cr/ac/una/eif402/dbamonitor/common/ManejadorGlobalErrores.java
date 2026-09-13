package cr.ac.una.eif402.dbamonitor.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

/**
 * Traduce las excepciones tecnicas a mensajes comprensibles para el
 * administrador, sin exponer trazas internas ni datos de conexion.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    @ExceptionHandler(ExcepcionSgbd.class)
    public ResponseEntity<RespuestaError> manejarErrorSgbd(ExcepcionSgbd ex, HttpServletRequest solicitud) {
        LOG.warn("Error controlado del SGBD: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(RespuestaError.de(HttpStatus.BAD_GATEWAY.value(), ex.getMessage(),
                        ex.getSugerencia(), solicitud.getRequestURI()));
    }

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<RespuestaError> manejarConexion(CannotGetJdbcConnectionException ex,
                                                          HttpServletRequest solicitud) {
        LOG.error("No fue posible conectar con SQL Server", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(RespuestaError.de(HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "No hay conexion con la instancia de SQL Server.",
                        "Verifique que el servicio este iniciado, que el puerto 1433 este habilitado "
                                + "y que las credenciales del perfil activo sean correctas.",
                        solicitud.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespuestaError> manejarArgumento(IllegalArgumentException ex,
                                                           HttpServletRequest solicitud) {
        return ResponseEntity.badRequest()
                .body(RespuestaError.de(HttpStatus.BAD_REQUEST.value(), ex.getMessage(),
                        "Revise los parametros enviados en la solicitud.", solicitud.getRequestURI()));
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<RespuestaError> manejarSinResultados(EmptyResultDataAccessException ex,
                                                               HttpServletRequest solicitud) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(RespuestaError.de(HttpStatus.NOT_FOUND.value(),
                        "La consulta no devolvio resultados.",
                        "Puede ser una condicion valida: por ejemplo, no hay sesiones bloqueadas.",
                        solicitud.getRequestURI()));
    }

    @ExceptionHandler({SQLException.class, org.springframework.dao.DataAccessException.class})
    public ResponseEntity<RespuestaError> manejarSql(Exception ex, HttpServletRequest solicitud) {
        LOG.error("Error al ejecutar la consulta administrativa", ex);
        String mensaje = ex.getMessage() == null ? "Error al ejecutar la consulta." : ex.getMessage();
        String sugerencia = mensaje.contains("VIEW SERVER STATE")
                ? "El usuario de la aplicacion necesita el permiso VIEW SERVER STATE. "
                  + "Ejecute el script sql/04_permisos_monitoreo.sql."
                : "Consulte el detalle en la bitacora del servidor de aplicaciones.";
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(RespuestaError.de(HttpStatus.BAD_GATEWAY.value(),
                        "La instancia rechazo la consulta administrativa.", sugerencia,
                        solicitud.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> manejarGeneral(Exception ex, HttpServletRequest solicitud) {
        LOG.error("Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespuestaError.de(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Ocurrio un error inesperado en la herramienta.",
                        "Reintente la operacion. Si persiste, revise la bitacora de la aplicacion.",
                        solicitud.getRequestURI()));
    }
}
