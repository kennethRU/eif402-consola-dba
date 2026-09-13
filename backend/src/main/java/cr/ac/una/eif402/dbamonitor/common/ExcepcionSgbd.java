package cr.ac.una.eif402.dbamonitor.common;

/**
 * Error controlado al interactuar con el SGBD: fallo de conexion,
 * permisos insuficientes o metrica no disponible en la edicion instalada.
 */
public class ExcepcionSgbd extends RuntimeException {

    private final String sugerencia;

    public ExcepcionSgbd(String mensaje, String sugerencia, Throwable causa) {
        super(mensaje, causa);
        this.sugerencia = sugerencia;
    }

    public ExcepcionSgbd(String mensaje, String sugerencia) {
        this(mensaje, sugerencia, null);
    }

    public String getSugerencia() {
        return sugerencia;
    }
}
