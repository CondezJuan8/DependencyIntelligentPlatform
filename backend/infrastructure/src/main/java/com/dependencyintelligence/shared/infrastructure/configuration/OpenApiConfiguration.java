package com.dependencyintelligence.shared.infrastructure.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    private static final String CSRF_SCHEME = "csrfToken";

    @Bean
    OpenAPI dependencyIntelligenceOpenApi() {
        return new OpenAPI()
            .info(new Info().title("Dependency Intelligence API").version("v1")
                .description("La sesion se autentica mediante la cookie JSESSIONID del login. Para operaciones que modifican datos, usa X-CSRF-TOKEN. El token CSRF no sustituye el inicio de sesion ni es un Bearer token."))
            .components(new Components().addSecuritySchemes(CSRF_SCHEME,
                new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-CSRF-TOKEN")
                    .description("Token CSRF de tu sesion actual. Obtenlo en GET /api/auth/csrf despues de iniciar sesion en este mismo navegador.")))
            .addSecurityItem(new SecurityRequirement().addList(CSRF_SCHEME));
    }
}
