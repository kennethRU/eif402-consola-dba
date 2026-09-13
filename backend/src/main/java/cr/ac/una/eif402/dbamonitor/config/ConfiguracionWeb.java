package cr.ac.una.eif402.dbamonitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Habilita el consumo de la API desde el cliente React en desarrollo. */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final PropiedadesDba propiedades;

    public ConfiguracionWeb(PropiedadesDba propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/api/**")
                .allowedOrigins(propiedades.getOrigenesPermitidos().split(","))
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}
