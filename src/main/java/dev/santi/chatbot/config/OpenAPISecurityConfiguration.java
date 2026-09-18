package dev.santi.chatbot.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Santiago Varela Daza
 * @version 1.0
 * @license Copyright © 2025 Santiago Varela Daza
 * @email svarela03@uan.edu.co
 * @github https://github.com/sanvarela03
 * @since 9/17/2026
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Chat Bot API",
                version = "${api.version}",
                contact = @Contact(
                        name = "Santiago", email = "svarela03@uan.edu.co", url = "https://www.linkedin.com/in/santiago-varela-b33917165/"
                ),
                license = @License(
                        name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"
                ),
                termsOfService = "${tos.uri:https://nexum.com/terms}",
                description = "${api.description}",
                extensions = @Extension(
                        name = "Prueba nombre extens",
                        properties=@ExtensionProperty(
                                name = "nombre",
                                value = "valor"
                        )
                )
        ),
        servers = {
                @Server(
                        url = "${api.server.local.url}",
                        description = "Local"
                ),
                @Server(
                        url = "${api.server.remote.url}",
                        description = "Produccion"
                )
        }
)
public class OpenAPISecurityConfiguration {
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("Todos")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public GroupedOpenApi webhookGroup() {
        return GroupedOpenApi.builder()
                .group("Mensajería")
                .pathsToMatch("/webhook/**")
                .build();
    }
}
