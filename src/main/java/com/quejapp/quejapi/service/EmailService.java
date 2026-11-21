package com.quejapp.quejapi.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Envía correos por la API HTTPS de Brevo (no por SMTP).
 * Render (plan gratis) bloquea los puertos SMTP 25, 465 y 587, por eso JavaMailSender no funcionaría allí.
 */
@Slf4j
@Service
public class EmailService {

    @Value("${brevo.api-key:}")
    private String apiKey;

    @Value("${brevo.sender-email:}")
    private String senderEmail;

    @Value("${brevo.sender-name:QuejApp}")
    private String senderName;

    private final RestTemplate rest;

    public EmailService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(15_000);
        this.rest = new RestTemplate(f);
    }

    public void sendResetCode(String to, String name, String code) {
        if (apiKey.isBlank() || senderEmail.isBlank()) {
            log.error("Brevo no está configurado: define BREVO_API_KEY y BREVO_SENDER_EMAIL");
            return;
        }
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:auto\">"
                + "<h2 style=\"color:#F37003\">QuejApp</h2>"
                + "<p>Hola " + escape(name) + ",</p>"
                + "<p>Usa este código para restablecer tu contraseña:</p>"
                + "<p style=\"font-size:34px;font-weight:bold;letter-spacing:8px;color:#0F172A\">" + code + "</p>"
                + "<p>El código vence en 15 minutos. Si no lo pediste tú, ignora este correo.</p></div>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", to)),
                "subject", "Tu código para recuperar la contraseña - QuejApp",
                "htmlContent", html);

        try {
            rest.postForEntity("https://api.brevo.com/v3/smtp/email", new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo de recuperación: {}", e.getMessage());
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
