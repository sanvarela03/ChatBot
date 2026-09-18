package dev.santi.chatbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Controlador principal de Webhooks para la integración con WhatsApp Cloud API de Meta.
 * Gestiona la verificación inicial de suscripción y el procesamiento de mensajes entrantes en tiempo real.
 *
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
@Tag(
        name = "WhatsApp Webhook",
        description = "Endpoints para la verificación y recepción de eventos en tiempo real de la API de WhatsApp Cloud de Meta"
)
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
    @Operation(
            summary = "Verificación del Webhook de Meta",
            description = "Endpoint utilizado exclusivamente por los servidores de Meta para validar el webhook durante su configuración. Comprueba el token de verificación y devuelve el challenge si coincide."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Webhook validado exitosamente. Retorna el valor numérico/alfanumérico de hub.challenge.",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "1158201444"))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Token de verificación inválido. Acceso rechazado.",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Token inválido"))
            )
    })
    public ResponseEntity<String> verificarWebhook(
            @Parameter(description = "Modo de suscripción enviado por Meta (debe ser 'subscribe')", example = "subscribe", required = true)
            @RequestParam("hub.mode") String mode,

            @Parameter(description = "Token de verificación configurado previamente en Meta Developers y en la aplicación", example = "MiPasswordBot123!", required = true)
            @RequestParam("hub.verify_token") String token,

            @Parameter(description = "Cadena enviada por Meta que debe ser devuelta en caso de que la verificación sea correcta", example = "1158201444", required = true)
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
    @Operation(
            summary = "Recepción de Mensajes y Eventos en Tiempo Real",
            description = "Endpoint donde Meta entrega en tiempo real los mensajes enviados por los clientes y las actualizaciones de estado. Procesa el texto recibido mediante un árbol de decisiones y responde automáticamente por WhatsApp."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Evento procesado correctamente. Meta requiere una respuesta HTTP 200 inmediata para no reintentar el envío del evento."
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Payload JSON estructurado enviado por Meta con los datos del mensaje o estado",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "Ejemplo de Mensaje de Texto",
                            value = "{\n  \"object\": \"whatsapp_business_account\",\n  \"entry\": [{\n    \"id\": \"1077649671294872\",\n    \"changes\": [{\n      \"value\": {\n        \"messaging_product\": \"whatsapp\",\n        \"metadata\": {\n          \"display_phone_number\": \"15551450471\",\n          \"phone_number_id\": \"1280150188525932\"\n        },\n        \"contacts\": [{\n          \"profile\": { \"name\": \"Santiago\" },\n          \"wa_id\": \"573001234567\"\n        }],\n        \"messages\": [{\n          \"from\": \"573001234567\",\n          \"id\": \"wamid.HBgL...\",\n          \"timestamp\": \"1726638000\",\n          \"text\": { \"body\": \"Hola\" },\n          \"type\": \"text\"\n        }]\n      },\n      \"field\": \"messages\"\n    }]\n  }]\n}"
                    )
            )
    )
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
