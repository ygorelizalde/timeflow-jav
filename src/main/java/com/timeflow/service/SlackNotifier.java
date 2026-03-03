package com.timeflow.service;

import com.timeflow.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * SlackNotifier — envia mensagens para um canal Slack via Incoming Webhook.
 *
 * Configuração: adicione em AppConfig.java:
 *   public static final String SLACK_WEBHOOK_URL = "https://hooks.slack.com/services/SEU/WEBHOOK/AQUI";
 *   public static final boolean SLACK_ENABLED = true;
 *
 * Uso no AgenteInvisivel:
 *   slackNotifier.notificarInicio(tarefa, projeto);
 *   slackNotifier.notificarFim(tarefa, projeto, duracaoSeg, score);
 *   slackNotifier.notificarSync(tarefa, duracaoSeg, score);
 */
public class SlackNotifier {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final DateTimeFormatter HORA_FMT =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final String webhookUrl;
    private final boolean enabled;
    private final String nomeUsuario; // nome do membro que está trackando

    public SlackNotifier(String webhookUrl, boolean enabled, String nomeUsuario) {
        this.webhookUrl  = webhookUrl;
        this.enabled     = enabled;
        this.nomeUsuario = nomeUsuario;
    }

    // ── Notificações públicas ─────────────────────────────────────────

    /** Chamado quando iniciarTracking() é acionado */
    public void notificarInicio(String tarefa, String projeto) {
        if (!enabled) return;
        String hora = HORA_FMT.format(Instant.now());
        String payload = buildPayload(
            ":green_circle:",
            "*" + nomeUsuario + "* iniciou uma sessão",
            new Field("Tarefa",  tarefa.isEmpty()  ? "_sem descrição_" : tarefa),
            new Field("Projeto", projeto.isEmpty() ? "_sem projeto_"   : projeto),
            new Field("Início",  hora),
            new Field("Status",  "Em andamento")
        );
        enviarAsync(payload);
    }

    /** Chamado quando pararTracking() é acionado */
    public void notificarFim(String tarefa, String projeto, long duracaoSegundos, double score) {
        if (!enabled) return;
        String hora     = HORA_FMT.format(Instant.now());
        String duracao  = formatarDuracao(duracaoSegundos);
        String emoji    = score >= 70 ? ":star:" : score >= 40 ? ":large_blue_circle:" : ":white_circle:";
        String payload  = buildPayload(
            ":red_circle:",
            "*" + nomeUsuario + "* encerrou uma sessão",
            new Field("Tarefa",        tarefa.isEmpty()  ? "_sem descrição_" : tarefa),
            new Field("Projeto",       projeto.isEmpty() ? "_sem projeto_"   : projeto),
            new Field("Duração",       duracao),
            new Field("Produtividade", emoji + " " + score + "%"),
            new Field("Encerrado às",  hora)
        );
        enviarAsync(payload);
    }

    /** Chamado a cada sync periódico (resumo ao vivo) */
    public void notificarSync(String tarefa, long duracaoSegundos, double score) {
        if (!enabled) return;
        // Só notifica a cada 30 min para não spammar
        if (duracaoSegundos % 1800 > 60) return;

        String duracao = formatarDuracao(duracaoSegundos);
        String emoji   = score >= 70 ? ":fire:" : score >= 40 ? ":large_blue_circle:" : ":zzz:";
        String payload = buildPayload(
            ":clock3:",
            "*" + nomeUsuario + "* — atualização de sessão",
            new Field("Tarefa",        tarefa.isEmpty() ? "_sem descrição_" : tarefa),
            new Field("Tempo ativo",   duracao),
            new Field("Produtividade", emoji + " " + score + "%")
        );
        enviarAsync(payload);
    }

    // ── Builder de payload Slack Block Kit ────────────────────────────

    private static record Field(String label, String value) {}

    private String buildPayload(String emoji, String titulo, Field... fields) {
        StringBuilder fieldsJson = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            fieldsJson.append(String.format("""
                {
                  "type": "mrkdwn",
                  "text": "*%s*\\n%s"
                }""", fields[i].label(), escape(fields[i].value())));
            if (i < fields.length - 1) fieldsJson.append(",\n");
        }

        return String.format("""
            {
              "blocks": [
                {
                  "type": "section",
                  "text": {
                    "type": "mrkdwn",
                    "text": "%s  %s"
                  }
                },
                {
                  "type": "section",
                  "fields": [
                    %s
                  ]
                },
                {
                  "type": "divider"
                }
              ]
            }""", emoji, escape(titulo), fieldsJson);
    }

    // ── HTTP ──────────────────────────────────────────────────────────

    private void enviarAsync(String payload) {
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(8))
                    .build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    System.err.println("[Slack] Erro HTTP " + resp.statusCode() + ": " + resp.body());
                }
            } catch (Exception e) {
                System.err.println("[Slack] Falha ao enviar: " + e.getMessage());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String formatarDuracao(long segundos) {
        long h = segundos / 3600;
        long m = (segundos % 3600) / 60;
        long s = segundos % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return segundos + "s";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}