package io.github.diegofranciscog.accounting.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI contabilidadOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("contabilidad-api")
                        .version("1.0.0")
                        .description("Motor de partida doble: plan de cuentas (Supercias), asientos, libro diario, "
                                + "mayor, balance de comprobación y estados financieros (NIIF para las PYMES). "
                                + "Importes en USD con dos decimales.")
                        .license(new License().name("MIT").url("https://opensource.org/license/mit")))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("hmac", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Signature")
                                .description("sha256=hex(HMAC_SHA256(secreto, X-Timestamp + \".\" + cuerpo))")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
