package com.hexbank.identity.config;

import com.hexbank.identity.adapter.in.http.DevHeaderAuthenticationFilter;
import com.hexbank.shared.adapter.in.http.RequestIdFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Ensina o Swagger UI a enviar os headers da identidade simulada.
 * Só existe em dev/test, igual ao DevHeaderAuthenticationFilter.
 * Fora desses profiles o springdoc fica desligado (application.yaml).
 */
@Configuration
@Profile({"dev", "test"})
public class DevOpenApiConfig {

    static final String PRINCIPAL_SCHEME = "devPrincipal";
    static final String ROLES_SCHEME = "devRoles";

    @Bean
    OpenAPI devOpenApi() {
        return new OpenAPI()
                .info(new Info().title("HEX BANK API").version("v1")
                        .description("""
                                Laboratório educacional — não movimenta dinheiro real.

                                **Identidade simulada (dev/test):** clique em *Authorize* e informe um UUID em \
                                `%s`. Para rotas `/v1/dev/**` informe também `TEST_ADMIN` em `%s`.

                                Erros seguem `application/problem+json` com `code` e `requestId`. \
                                Envie `%s` para correlacionar logs.
                                """.formatted(DevHeaderAuthenticationFilter.PRINCIPAL_HEADER,
                                DevHeaderAuthenticationFilter.ROLES_HEADER, RequestIdFilter.HEADER)))
                .components(new Components()
                        .addSecuritySchemes(PRINCIPAL_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(DevHeaderAuthenticationFilter.PRINCIPAL_HEADER)
                                .description("UUID do usuário simulado"))
                        .addSecuritySchemes(ROLES_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(DevHeaderAuthenticationFilter.ROLES_HEADER)
                                .description("Opcional. Papéis separados por vírgula. Ex.: TEST_ADMIN")))
                .addSecurityItem(new SecurityRequirement().addList(PRINCIPAL_SCHEME).addList(ROLES_SCHEME));
    }
}
