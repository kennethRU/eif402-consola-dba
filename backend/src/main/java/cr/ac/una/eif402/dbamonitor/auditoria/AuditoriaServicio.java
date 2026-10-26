package cr.ac.una.eif402.dbamonitor.auditoria;

import cr.ac.una.eif402.dbamonitor.auditoria.AuditoriaDtos.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Modulo 5. Recupera la informacion de seguridad y genera observaciones
 * sobre configuraciones de riesgo frecuentes.
 */
@Service
public class AuditoriaServicio {

    private final AuditoriaRepositorio repositorio;

    public AuditoriaServicio(AuditoriaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public ResumenModuloAuditoria obtenerResumen() {
        List<String> advertencias = new ArrayList<>();

        List<LoginServidor> logins = repositorio.logins();
        List<UsuarioBaseDatos> usuarios = repositorio.usuarios();
        List<AsignacionRol> roles = repositorio.roles();
        List<Privilegio> privilegios = repositorio.privilegios();
        List<ObjetoConProblema> objetos = repositorio.objetosConProblemas();

        advertencias.add("A diferencia de Oracle, SQL Server no marca objetos como invalidos. "
                + "Se detectan dependencias rotas y, en el modulo de mantenimiento, se valida "
                + "cada modulo con sp_refreshsqlmodule.");

        return new ResumenModuloAuditoria(logins, usuarios, roles, privilegios, objetos,
                evaluarSeguridad(logins, privilegios), advertencias);
    }

    /** Observaciones de seguridad derivadas de los datos consultados. */
    static String evaluarSeguridad(List<LoginServidor> logins, List<Privilegio> privilegios) {
        List<String> hallazgos = new ArrayList<>();

        long sysadmins = logins.stream().filter(LoginServidor::esSysadmin).count();
        if (sysadmins > 2) {
            long porGrupo = logins.stream()
                    .filter(login -> login.esSysadmin() && !login.sysadminDirecto())
                    .count();
            hallazgos.add(sysadmins + " logins tienen privilegios sysadmin"
                    + (porGrupo > 0 ? " (" + porGrupo + " por pertenencia a grupo)" : ""));
        }

        boolean saHabilitado = logins.stream()
                .anyMatch(login -> "sa".equalsIgnoreCase(login.login()) && !login.deshabilitado());
        if (saHabilitado) {
            hallazgos.add("la cuenta 'sa' esta habilitada");
        }

        long permisosPublicos = privilegios.stream()
                .filter(privilegio -> "CONTROL".equalsIgnoreCase(privilegio.privilegio())
                        && "GRANT".equalsIgnoreCase(privilegio.estado()))
                .count();
        if (permisosPublicos > 0) {
            hallazgos.add(permisosPublicos + " concesion(es) del privilegio CONTROL");
        }

        if (hallazgos.isEmpty()) {
            return "No se detectaron configuraciones de riesgo evidentes en usuarios y privilegios.";
        }
        return "Puntos de atencion: " + String.join("; ", hallazgos) + ".";
    }
}
