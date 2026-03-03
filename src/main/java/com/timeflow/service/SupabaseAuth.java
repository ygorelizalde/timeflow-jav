package com.timeflow.service;

import com.timeflow.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * SupabaseAuth — login e gestão de sessão do usuário.
 *
 * Uso no LoginController:
 *   SupabaseAuth.LoginResult result = SupabaseAuth.login(email, senha);
 *   if (result.sucesso()) {
 *       PerfilSession.set(result.perfilId(), result.empresaId(), result.nome(), result.token());
 *       // navegar para tela principal
 *   }
 */
public class SupabaseAuth {

    private static final Logger LOG = Logger.getLogger(SupabaseAuth.class.getName());

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // ── Login ─────────────────────────────────────────────────────────

    public record LoginResult(
        boolean sucesso,
        String  perfilId,
        String  empresaId,
        String  nome,
        String  nivel,
        String  token,
        String  erro
    ) {
        public static LoginResult erro(String msg) {
            return new LoginResult(false, null, null, null, null, null, msg);
        }
    }

    /**
     * Autentica com email e senha no Supabase Auth,
     * depois busca o perfil do usuário na tabela 'perfis'.
     */
    public static LoginResult login(String email, String senha) {
        try {
            // 1. Autenticar no Supabase Auth
            String authBody = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\"}",
                escape(email), escape(senha)
            );

            HttpResponse<String> authResp = post(
                "/auth/v1/token?grant_type=password",
                authBody,
                null
            );

            if (authResp.statusCode() != 200) {
                LOG.warning("[Auth] Login falhou: " + authResp.statusCode());
                return LoginResult.erro("Email ou senha incorretos.");
            }

            // Extrai o access_token e user id do JSON
            String authJson = authResp.body();
            String token    = extrairJson(authJson, "access_token");
            String userId   = extrairJsonAninhado(authJson, "user", "id");

            if (token == null || userId == null) {
                return LoginResult.erro("Resposta inválida do servidor.");
            }

            // 2. Busca perfil do usuário
            HttpResponse<String> perfilResp = get(
                "/rest/v1/perfis?user_id=eq." + userId
                + "&select=id,nome,nivel,empresa_id,ativo",
                token
            );

            if (perfilResp.statusCode() != 200) {
                return LoginResult.erro("Perfil não encontrado.");
            }

            String perfilJson = perfilResp.body();

            // Remove colchetes do array JSON
            perfilJson = perfilJson.trim();
            if (perfilJson.startsWith("[")) perfilJson = perfilJson.substring(1);
            if (perfilJson.endsWith("]"))   perfilJson = perfilJson.substring(0, perfilJson.length() - 1);

            if (perfilJson.isBlank()) {
                return LoginResult.erro("Usuário sem perfil cadastrado.");
            }

            String perfilId  = extrairJson(perfilJson, "id");
            String nome      = extrairJson(perfilJson, "nome");
            String nivel     = extrairJson(perfilJson, "nivel");
            String empresaId = extrairJson(perfilJson, "empresa_id");
            String ativo     = extrairJson(perfilJson, "ativo");

            if ("false".equals(ativo)) {
                return LoginResult.erro("Conta desativada. Contate o gestor.");
            }

            LOG.info("[Auth] Login OK  user=" + nome + "  nivel=" + nivel);
            return new LoginResult(true, perfilId, empresaId, nome, nivel, token, null);

        } catch (Exception e) {
            LOG.warning("[Auth] Erro inesperado: " + e.getMessage());
            return LoginResult.erro("Erro de conexão. Verifique sua internet.");
        }
    }

    // ── Logout ────────────────────────────────────────────────────────

    public static void logout(String token) {
        try {
            post("/auth/v1/logout", "{}", token);
            LOG.info("[Auth] Logout OK");
        } catch (Exception e) {
            LOG.warning("[Auth] Erro no logout: " + e.getMessage());
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────

    private static HttpResponse<String> post(String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(AppConfig.SUPABASE_URL + path))
            .header("Content-Type", "application/json")
            .header("apikey", AppConfig.SUPABASE_ANON_KEY)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10));
        if (token != null)
            builder.header("Authorization", "Bearer " + token);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(AppConfig.SUPABASE_URL + path))
            .header("apikey", AppConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer " + token)
            .GET()
            .timeout(Duration.ofSeconds(10));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ── JSON mínimo (sem dependência externa) ─────────────────────────

    static String extrairJson(String json, String campo) {
        String key = "\"" + campo + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first == '"') {
            int end = json.indexOf('"', start + 1);
            return end < 0 ? null : json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end).trim();
    }

    static String extrairJsonAninhado(String json, String objeto, String campo) {
        String key = "\"" + objeto + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int braceOpen = json.indexOf('{', idx);
        if (braceOpen < 0) return null;
        int braceClose = json.indexOf('}', braceOpen);
        if (braceClose < 0) return null;
        return extrairJson(json.substring(braceOpen, braceClose + 1), campo);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}