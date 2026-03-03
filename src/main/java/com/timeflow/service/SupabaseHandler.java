package com.timeflow.service;

import com.timeflow.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SupabaseHandler — sincroniza sessões e status do membro com o Supabase.
 *
 * Complementa o InfluxDB:
 *   - InfluxDB → analytics detalhado (séries temporais, app usage)
 *   - Supabase → dashboard web (sessão ativa, horas hoje, status ao vivo)
 *
 * Tabelas usadas (já existem no projeto):
 *   - perfis         → atualiza sessao_ativa
 *   - sessoes_web    → registra início/fim de sessão (nova tabela)
 *
 * SQL para criar sessoes_web (rode no Supabase SQL Editor):
 * ─────────────────────────────────────────────────────────
 * CREATE TABLE IF NOT EXISTS sessoes_web (
 *   id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
 *   perfil_id         UUID REFERENCES perfis(id) ON DELETE CASCADE,
 *   empresa_id        UUID REFERENCES empresas(id),
 *   session_id        TEXT NOT NULL,
 *   descricao         TEXT,
 *   projeto           TEXT,
 *   inicio            TIMESTAMPTZ NOT NULL,
 *   fim               TIMESTAMPTZ,
 *   duracao_segundos  INTEGER DEFAULT 0,
 *   score_produtividade FLOAT DEFAULT 0,
 *   total_interacoes  INTEGER DEFAULT 0,
 *   ativo             BOOLEAN DEFAULT TRUE,
 *   criado_em         TIMESTAMPTZ DEFAULT NOW()
 * );
 * ALTER TABLE sessoes_web DISABLE ROW LEVEL SECURITY;
 * ─────────────────────────────────────────────────────────
 */
public class SupabaseHandler {

    private static final Logger LOG = Logger.getLogger(SupabaseHandler.class.getName());

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    private final String supabaseUrl;
    private final String supabaseKey;
    private final String perfilId;
    private final String empresaId;

    private String sessaoWebId = null; // UUID da linha em sessoes_web

    public SupabaseHandler(String perfilId, String empresaId) {
        this.supabaseUrl = AppConfig.SUPABASE_URL;
        this.supabaseKey = AppConfig.SUPABASE_ANON_KEY;
        this.perfilId    = perfilId;
        this.empresaId   = empresaId;
    }

    // ── Lifecycle de sessão ───────────────────────────────────────────

    /**
     * Chamado quando iniciarTracking() é acionado no AgenteInvisivel.
     * Cria linha em sessoes_web + marca perfil como ativo.
     */
    public void onIniciarTracking(String sessionId, String descricao, String projeto) {
        Thread.ofVirtual().start(() -> {
            try {
                // 1. Cria sessão no Supabase
                String body = String.format("""
                    {
                      "perfil_id": "%s",
                      "empresa_id": "%s",
                      "session_id": "%s",
                      "descricao": "%s",
                      "projeto": "%s",
                      "inicio": "%s",
                      "ativo": true
                    }""",
                    perfilId, empresaId,
                    escape(sessionId),
                    escape(descricao.isEmpty() ? "sem descrição" : descricao),
                    escape(projeto.isEmpty() ? "Sem Projeto" : projeto),
                    Instant.now()
                );

                HttpResponse<String> resp = post("/rest/v1/sessoes_web", body);

                if (resp.statusCode() == 201) {
                    // Pega o ID gerado (header Location ou body)
                    String location = resp.headers().firstValue("Location").orElse("");
                    if (!location.isEmpty()) {
                        sessaoWebId = location.replaceAll(".*/", "").replaceAll("[^a-f0-9\\-]", "");
                    }
                    LOG.info("[Supabase] Sessão criada  session=" + sessionId);
                } else {
                    LOG.warning("[Supabase] Erro ao criar sessão: " + resp.statusCode() + " " + resp.body());
                }

                // 2. Marca perfil como ativo
                marcarPerfilAtivo(true);

            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Supabase] Erro onIniciarTracking: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Chamado periodicamente pelo sync do AgenteInvisivel.
     * Atualiza horas e score da sessão em andamento.
     */
    public void onSync(String sessionId, long duracaoSegundos, double score, int totalInteracoes) {
        Thread.ofVirtual().start(() -> {
            try {
                String filtro = "session_id=eq." + sessionId;
                String body = String.format("""
                    {
                      "duracao_segundos": %d,
                      "score_produtividade": %.1f,
                      "total_interacoes": %d,
                      "ativo": true
                    }""", duracaoSegundos, score, totalInteracoes);

                patch("/rest/v1/sessoes_web?" + filtro, body);
                LOG.info("[Supabase] Sync OK  session=" + sessionId + "  dur=" + duracaoSegundos + "s  score=" + score);

            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Supabase] Erro onSync: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Chamado quando pararTracking() é acionado.
     * Finaliza a sessão e marca perfil como offline.
     */
    public void onPararTracking(String sessionId, long duracaoSegundos, double score, int totalInteracoes) {
        Thread.ofVirtual().start(() -> {
            try {
                // 1. Finaliza sessão
                String filtro = "session_id=eq." + sessionId;
                String body = String.format("""
                    {
                      "fim": "%s",
                      "duracao_segundos": %d,
                      "score_produtividade": %.1f,
                      "total_interacoes": %d,
                      "ativo": false
                    }""",
                    Instant.now(), duracaoSegundos, score, totalInteracoes);

                patch("/rest/v1/sessoes_web?" + filtro, body);

                // 2. Marca perfil como offline
                marcarPerfilAtivo(false);

                LOG.info("[Supabase] Sessão finalizada  session=" + sessionId);

            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Supabase] Erro onPararTracking: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Deve ser chamado no shutdown do app para garantir que o perfil
     * fique offline mesmo se o app fechar sem pararTracking().
     */
    public void onAppShutdown() {
        try {
            marcarPerfilAtivo(false);
            LOG.info("[Supabase] App shutdown — perfil marcado offline");
        } catch (Exception e) {
            LOG.warning("[Supabase] Erro no shutdown: " + e.getMessage());
        }
    }

    // ── Helpers internos ──────────────────────────────────────────────

    private void marcarPerfilAtivo(boolean ativo) throws Exception {
        String body = String.format("{\"sessao_ativa\": %b}", ativo);
        patch("/rest/v1/perfis?id=eq." + perfilId, body);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(supabaseUrl + path))
            .header("Content-Type", "application/json")
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer " + supabaseKey)
            .header("Prefer", "return=representation")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(supabaseUrl + path))
            .header("Content-Type", "application/json")
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer " + supabaseKey)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}