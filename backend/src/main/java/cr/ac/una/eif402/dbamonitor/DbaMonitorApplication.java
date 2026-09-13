package cr.ac.una.eif402.dbamonitor;

import cr.ac.una.eif402.dbamonitor.config.PropiedadesDba;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Punto de entrada de la herramienta de monitoreo, administracion y
 * auditoria para Microsoft SQL Server.
 *
 * Curso EIF402 - Administracion de Bases de Datos, Universidad Nacional.
 */
@SpringBootApplication
@EnableConfigurationProperties(PropiedadesDba.class)
public class DbaMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbaMonitorApplication.class, args);
    }
}
