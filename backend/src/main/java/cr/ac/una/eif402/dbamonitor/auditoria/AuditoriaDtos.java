package cr.ac.una.eif402.dbamonitor.auditoria;

import java.time.LocalDateTime;
import java.util.List;

/** Objetos de transferencia del Modulo 5: auditoria. */
public final class AuditoriaDtos {

    private AuditoriaDtos() { }

    public record LoginServidor(
            String login,
            String tipo,
            boolean deshabilitado,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaModificacion,
            String baseDatosPredeterminada,
            boolean esSysadmin
    ) { }

    public record UsuarioBaseDatos(
            String usuario,
            String tipo,
            LocalDateTime fechaCreacion,
            String esquemaPredeterminado,
            String loginAsociado,
            String tipoAutenticacion
    ) { }

    public record AsignacionRol(String ambito, String rol, String miembro, String tipoMiembro) { }

    public record Privilegio(
            String beneficiario,
            String tipoBeneficiario,
            String estado,
            String privilegio,
            String clase,
            String objeto
    ) { }

    public record ObjetoConProblema(
            String esquema,
            String objeto,
            String tipoObjeto,
            String entidadReferenciada,
            String diagnostico
    ) { }

    public record ResumenModuloAuditoria(
            List<LoginServidor> logins,
            List<UsuarioBaseDatos> usuarios,
            List<AsignacionRol> roles,
            List<Privilegio> privilegios,
            List<ObjetoConProblema> objetosConProblemas,
            String diagnosticoSeguridad,
            List<String> advertencias
    ) { }
}
