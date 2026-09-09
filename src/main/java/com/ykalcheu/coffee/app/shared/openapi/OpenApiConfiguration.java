package com.ykalcheu.coffee.app.shared.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Coffee App API",
                version = "v1",
                description = "Transactional coffee catalog and ordering API."
        )
)
public class OpenApiConfiguration {
}
