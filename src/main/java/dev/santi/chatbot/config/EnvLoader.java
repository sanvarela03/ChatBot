package dev.santi.chatbot.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;

/**
 * @author Santiago Varela Daza
 * @version 1.0
 * @license Copyright © 2025 Santiago Varela Daza
 * @email svarela03@uan.edu.co
 * @github <a href="https://github.com/sanvarela03"> sanvarela03</a>
 * @since 7/16/2025
 */
public class EnvLoader {

    // Principio SOLID (Open-Closed): Para escalar, solo haz append de tus nuevas variables a esta lista.
    private static final List<String> ENV_VARIABLES = List.of(
            "ACCESS_TOKEN_DE_META",
            "ID_DE_TU_TELEFONO_DE_PRUEBA",
            "MI_TOKEN_SECRETO"
    );

    static {
        // En local: lee del archivo .env. Si no existe (ej. en Docker/Render), no interrumpe el flujo.
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        for (String key : ENV_VARIABLES) {
            // Prioridad 1: Variable real del Sistema Operativo (inyectada por Render en producción)
            String value = System.getenv(key);

            // Prioridad 2: Variable extraída del archivo .env (Desarrollo local)
            if (value == null) {
                value = dotenv.get(key);
            }

            // Inyección al pool de la JVM para ser usado por application.yml u otro módulo de Spring Boot
            if (value != null) {
                System.setProperty(key, value);
            }
        }
    }

    public static void load() {
        try {
            new EnvLoader();
            System.out.println("✅ Variables de entorno cargadas correctamente");
        } catch (Exception e) {
            System.err.println("❌ Error al cargar variables de entorno: " + e.getMessage());
            throw new RuntimeException("❌ Error al cargar las variables de entorno", e);
        }
    }
}
