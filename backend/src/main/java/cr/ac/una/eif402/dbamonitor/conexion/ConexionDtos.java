package cr.ac.una.eif402.dbamonitor.conexion;

/** Objetos de transferencia del modulo de conexion. */
public final class ConexionDtos {

    private ConexionDtos() { }

    /** Perfil expuesto al cliente. Nunca incluye la contrasena. */
    public record PerfilResumen(
            String nombre,
            String descripcion,
            String servidor,
            String baseDatos,
            String usuario,
            boolean activo
    ) { }

    public record SolicitudCambioPerfil(String nombre) { }

    public record ResultadoOperacion(boolean exitoso, String mensaje) { }
}
