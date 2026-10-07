package com.gonggeumi.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer rejectScalarToStringCoercion() {
        // ALLOW_COERCION_OF_SCALARS=false alone does not forbid number -> string conversion.
        return builder -> builder.postConfigurer(mapper -> {
            var text = mapper.coercionConfigFor(LogicalType.Textual);
            text.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            text.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            text.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
