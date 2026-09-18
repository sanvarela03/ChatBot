package dev.santi.chatbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * @author Santiago Varela Daza
 * @version 1.0
 * @license Copyright © 2025 Santiago Varela Daza
 * @email svarela03@uan.edu.co
 * @github https://github.com/sanvarela03
 * @since 9/17/2026
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
public class WhatsAppWebhookController {

    @Value("${whatsapp.webhook.verify-token}")
    private String verifyToken;

    @Value("${whatsapp.api.token}")
    private String accessToken;

    @Value("${whatsapp.api.phone-id}")
    private String phoneId;

    @Value("${whatsapp.api.base-url:https://graph.facebook.com}")
    private String baseUrl;

    @Value("${whatsapp.api.version:v21.0}")
    private String apiVersion;

    private final RestClient restClient = RestClient.create();

    @GetMapping
    public ResponseEntity<String> verificarWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook verificado exitosamente por Meta.");
            return ResponseEntity.ok(challenge);
        }
        log.warn("Intento fallido de verificación del webhook. Token no coincide.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Token inválido");
    }

    /**
     * Recibe los mensajes enviados por los usuarios en tiempo real.
     */
    @PostMapping
    public ResponseEntity<Void> recibirMensaje(@RequestBody JsonNode payload) {
        try {
            JsonNode entries = payload.path("entry");
            if (!entries.isArray() || entries.isEmpty()) {
                return ResponseEntity.ok().build();
            }

            for (JsonNode entry : entries) {
                JsonNode changes = entry.path("changes");
                if (!changes.isArray()) continue;

                for (JsonNode change : changes) {
                    JsonNode value = change.path("value");

                    // Ignorar notificaciones de estado (sent, delivered, read)
                    if (value.has("statuses")) {
                        log.debug("Notificación de estado recibida (delivery/read receipt)");
                        continue;
                    }

                    if (value.has("messages") && value.path("messages").isArray()) {
                        for (JsonNode message : value.path("messages")) {
                            String tipoMensaje = message.path("type").asText();
                            String numeroCliente = message.path("from").asText();

                            if ("text".equalsIgnoreCase(tipoMensaje)) {
                                String textoRecibido = message.path("text").path("body").asText().toLowerCase();
                                log.info("Mensaje recibido de {}: {}", numeroCliente, textoRecibido);

                                String respuesta = procesarRespuesta(textoRecibido);
                                enviarMensaje(numeroCliente, respuesta);
                            } else {
                                log.info("Mensaje no de texto ({}) recibido de {}", tipoMensaje, numeroCliente);
                                enviarMensaje(numeroCliente, "Lo siento, por ahora solo puedo responder a mensajes de texto.");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error procesando payload del webhook de WhatsApp", e);
        }

        // Meta exige código 200 de inmediato para no reintentar el webhook
        return ResponseEntity.ok().build();
    }

    /**
     * Árbol de decisiones básico para el bot.
     */
    private String procesarRespuesta(String texto) {
        if (texto.contains("hola") || texto.contains("buenos dias") || texto.contains("buenas tardes")) {
            return "¡Hola desde Spring Boot! 🤖 ¿En qué puedo ayudarte hoy?";
        } else if (texto.contains("precio") || texto.contains("costo") || texto.contains("planes")) {
            return "Nuestros planes comienzan en $50 USD. Escribe 'soporte' para hablar con un humano.";
        } else if (texto.contains("gracias") || texto.contains("adios") || texto.contains("chao")) {
            return "¡Un placer ayudarte! ¡Hasta pronto!";
        } else {
            return "Lo siento, soy un bot básico. Escribe 'hola' para ver las opciones disponibles.";
        }
    }

    /**
     * Conexión con la API de Meta usando el RestClient de Spring.
     */
    private void enviarMensaje(String telefonoDestino, String textoRespuesta) {
        String url = String.format("%s/%s/%s/messages", baseUrl, apiVersion, phoneId);

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", telefonoDestino,
                "type", "text",
                "text", Map.of("body", textoRespuesta)
        );

        try {
            restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Mensaje enviado exitosamente a {}", telefonoDestino);
        } catch (Exception e) {
            log.error("Error al enviar mensaje a través de la API de WhatsApp a {}", telefonoDestino, e);
        }
    }
}
