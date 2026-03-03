package com.timeflow.model;

import java.time.Instant;
import java.util.Map;

/**
 * Session — modelo de uma sessão de trabalho rastreada.
 */
public class Session {

    private String  sessionId;
    private String  descricao;
    private String  projeto;
    private String  usuario;
    private Instant inicio;
    private Instant fim;
    private long    duracaoSegundos;
    private String  duracaoFormatada;
    private double  scoreProdutividade;
    private Map<String, AppUsage> aplicativos;

    // ── Builder estático ──────────────────────────────────────────────
    public static Session fromMap(Map<String, Object> m) {
        Session s = new Session();
        s.sessionId          = str(m.get("session_id"));
        s.descricao          = str(m.get("descricao"));
        s.projeto            = str(m.getOrDefault("projeto", "Sem Projeto"));
        s.usuario            = str(m.get("usuario"));
        s.duracaoSegundos    = num(m.get("duracao_segundos"));
        s.duracaoFormatada   = str(m.getOrDefault("duracao_formatada","0m"));
        s.scoreProdutividade = dbl(m.get("score_produtividade"));
        Object t = m.get("fim");
        if (t instanceof Instant i) s.fim = i;
        return s;
    }

    // ── Getters ───────────────────────────────────────────────────────
    public String  getSessionId()          { return sessionId; }
    public String  getDescricao()          { return descricao; }
    public String  getProjeto()            { return projeto; }
    public String  getUsuario()            { return usuario; }
    public Instant getInicio()             { return inicio; }
    public Instant getFim()               { return fim; }
    public long    getDuracaoSegundos()   { return duracaoSegundos; }
    public String  getDuracaoFormatada()  { return duracaoFormatada; }
    public double  getScoreProdutividade(){ return scoreProdutividade; }

    // ── Helpers ───────────────────────────────────────────────────────
    private static String str(Object v) { return v == null ? "" : v.toString(); }
    private static long   num(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(str(v)); } catch (Exception e) { return 0; }
    }
    private static double dbl(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(str(v)); } catch (Exception e) { return 0; }
    }
}
