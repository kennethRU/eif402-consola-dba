package cr.ac.una.eif402.dbamonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuracion externa de la herramienta. Permite definir varios perfiles
 * de conexion sin modificar el codigo fuente.
 */
@ConfigurationProperties(prefix = "dba")
public class PropiedadesDba {

    private String origenesPermitidos = "http://localhost:5173";
    private String perfilInicial;
    private List<PerfilConexion> conexiones = new ArrayList<>();

    public String getOrigenesPermitidos() { return origenesPermitidos; }
    public void setOrigenesPermitidos(String origenesPermitidos) { this.origenesPermitidos = origenesPermitidos; }

    public String getPerfilInicial() { return perfilInicial; }
    public void setPerfilInicial(String perfilInicial) { this.perfilInicial = perfilInicial; }

    public List<PerfilConexion> getConexiones() { return conexiones; }
    public void setConexiones(List<PerfilConexion> conexiones) { this.conexiones = conexiones; }

    /** Datos de conexion a una instancia de SQL Server. */
    public static class PerfilConexion {
        private String nombre;
        private String descripcion;
        private String host = "localhost";
        private int puerto = 1433;
        /** Nombre de instancia nombrada. Vacio para la instancia predeterminada. */
        private String instancia;
        private String baseDatos;
        private String usuario;
        private String contrasena;
        private boolean cifrado = true;
        private boolean confiarCertificadoServidor = true;

        /**
         * Construye la URL JDBC. Cuando se usa una instancia nombrada se
         * indica con instanceName y no se especifica el puerto, porque lo
         * resuelve el servicio SQL Server Browser.
         */
        public String construirUrlJdbc() {
            StringBuilder url = new StringBuilder("jdbc:sqlserver://").append(host);
            boolean tieneInstancia = instancia != null && !instancia.isBlank();
            if (!tieneInstancia) {
                url.append(':').append(puerto);
            }
            url.append(";databaseName=").append(baseDatos);
            if (tieneInstancia) {
                url.append(";instanceName=").append(instancia);
            }
            url.append(";encrypt=").append(cifrado)
               .append(";trustServerCertificate=").append(confiarCertificadoServidor)
               .append(";loginTimeout=10")
               .append(";applicationName=DBA Monitor EIF402");
            return url.toString();
        }

        /** Descripcion legible de la conexion, sin exponer la contrasena. */
        public String resumen() {
            String destino = (instancia == null || instancia.isBlank())
                    ? host + ":" + puerto
                    : host + "\\" + instancia;
            return destino + " / " + baseDatos + " (usuario: " + usuario + ")";
        }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPuerto() { return puerto; }
        public void setPuerto(int puerto) { this.puerto = puerto; }
        public String getInstancia() { return instancia; }
        public void setInstancia(String instancia) { this.instancia = instancia; }
        public String getBaseDatos() { return baseDatos; }
        public void setBaseDatos(String baseDatos) { this.baseDatos = baseDatos; }
        public String getUsuario() { return usuario; }
        public void setUsuario(String usuario) { this.usuario = usuario; }
        public String getContrasena() { return contrasena; }
        public void setContrasena(String contrasena) { this.contrasena = contrasena; }
        public boolean isCifrado() { return cifrado; }
        public void setCifrado(boolean cifrado) { this.cifrado = cifrado; }
        public boolean isConfiarCertificadoServidor() { return confiarCertificadoServidor; }
        public void setConfiarCertificadoServidor(boolean v) { this.confiarCertificadoServidor = v; }
    }
}
