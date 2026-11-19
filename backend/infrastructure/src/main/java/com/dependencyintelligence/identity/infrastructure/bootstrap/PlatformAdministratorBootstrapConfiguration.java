package com.dependencyintelligence.identity.infrastructure.bootstrap;

import com.dependencyintelligence.identity.application.dto.BootstrapPlatformAdministratorCommand;
import com.dependencyintelligence.identity.application.port.in.BootstrapPlatformAdministrator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.io.Console;
import java.security.MessageDigest;
import java.util.Arrays;

/** One-shot local operator command; absent from the normal web profile. */
@Configuration
@Profile("platform-admin-bootstrap")
public class PlatformAdministratorBootstrapConfiguration {
    @Bean
    ApplicationRunner platformAdministratorBootstrap(BootstrapPlatformAdministrator bootstrap,
            ConfigurableApplicationContext context) {
        return (ApplicationArguments ignored) -> {
            try {
                Console console = System.console();
                if (console == null) {
                    throw new IllegalStateException("Se requiere una consola interactiva. Ejecuta el comando indicado con -it.");
                }
                String confirmationPhrase = console.readLine("Escribe CREAR ADMIN para confirmar el bootstrap: ");
                if (!"CREAR ADMIN".equals(confirmationPhrase)) {
                    throw new IllegalStateException("Bootstrap cancelado.");
                }
                String email = console.readLine("Correo del administrador inicial: ");
                String displayName = console.readLine("Nombre completo: ");
                char[] password = console.readPassword("Contrasena inicial (15 a 128 caracteres): ");
                char[] confirmation = console.readPassword("Confirma la contrasena: ");
                try {
                    if (password == null || confirmation == null || !MessageDigest.isEqual(
                            new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            new String(confirmation).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        throw new IllegalArgumentException("La confirmacion de contrasena no coincide.");
                    }
                    bootstrap.execute(new BootstrapPlatformAdministratorCommand(
                        email, displayName, new String(password), new String(confirmation)));
                    console.printf("Administrador inicial creado y habilitado. Inicia sesion en http://localhost:%s%n",
                        System.getenv().getOrDefault("WEB_PORT", "8088"));
                } finally {
                    if (password != null) Arrays.fill(password, '\0');
                    if (confirmation != null) Arrays.fill(confirmation, '\0');
                }
            } finally {
                SpringApplication.exit(context);
            }
        };
    }
}
