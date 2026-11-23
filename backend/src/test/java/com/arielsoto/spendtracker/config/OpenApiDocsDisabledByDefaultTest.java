package com.arielsoto.spendtracker.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

// The OpenAPI spec and Swagger UI describe the whole API surface, so they must stay off in prod. They
// are off in the base application.yml and only the dev profile turns them on. The test classpath has
// its own application.yml, which hides the main one, so this reads the main file from disk (Gradle
// runs the tests from the backend directory).
class OpenApiDocsDisabledByDefaultTest {

    @Test
    void mainConfigurationTurnsOffTheSpecAndSwaggerUi() {
        Properties properties = load("src/main/resources/application.yml");

        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void prodProfileDoesNotTurnThemOn() {
        Properties properties = load("src/main/resources/application-prod.yml");

        assertThat(properties.stringPropertyNames()).noneMatch(name -> name.startsWith("springdoc."));
    }

    private static Properties load(String path) {
        FileSystemResource file = new FileSystemResource(path);
        assertThat(file.exists()).as(path).isTrue();

        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(file);
        return yaml.getObject();
    }
}
