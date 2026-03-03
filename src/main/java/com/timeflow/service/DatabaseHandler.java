package com.timeflow.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.exceptions.InfluxException;
import com.timeflow.config.AppConfig;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DatabaseHandler — equivalente ao database.py.
 * Gerencia a conexão e escrita/leitura no InfluxDB Cloud.
 */
public class DatabaseHandler {

    private static final Logger LOG = Logger.getLogger(DatabaseHandler.class.getName());

    private InfluxDBClient client;
    private WriteApiBlocking writeApi;
    private QueryApi         queryApi;
    private boolean          conectado = false;

    public DatabaseHandler() {
        inicializar();
    }

    // ── Conexão ───────────────────────────────────────────────────────

    public void inicializar() {
        try {
            client = InfluxDBClientFactory.create(
                AppConfig.INFLUX_URL,
                AppConfig.INFLUX_TOKEN.toCharArray(),
                AppConfig.INFLUX_ORG,
                AppConfig.INFLUX_BUCKET
            );
            writeApi = client.getWriteApiBlocking();
            queryApi = client.getQueryApi();

            // Ping via escrita de um ponto de teste
            Point teste = Point.measurement("_connection_test")
                .addTag("origem", "init")
                .addField("ok", 1)
                .time(Instant.now(), WritePrecision.NS);
            writeApi.writePoint(teste);

            conectado = true;
            LOG.info("[DB] InfluxDB conectado  bucket='" + AppConfig.INFLUX_BUCKET
                     + "'  org='" + AppConfig.INFLUX_ORG + "'");

        } catch (InfluxException e) {
            conectado = false;
            LOG.warning("[DB] InfluxException na conexão: " + e.getMessage());
        } catch (Exception e) {
            conectado = false;
            LOG.warning("[DB] Erro geral na conexão: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
        }
    }

    public boolean isConectado() { return conectado; }

    // ── Escrita ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public boolean salvarSessao(String sessionId, Map<String, Object> dados) {
        if (!conectado) { LOG.warning("[DB] salvarSessao: não conectado."); return false; }

        try {
            String usuario   = obterHostname();
            String descricao = (String) dados.getOrDefault("descricao", "sem_descricao");
            String projeto   = (String) dados.getOrDefault("projeto", "Sem Projeto");
            Instant fim      = (Instant) dados.getOrDefault("fim", Instant.now());

            if (descricao == null || descricao.isBlank()) descricao = "sem_descricao";
            if (projeto   == null || projeto.isBlank())   projeto   = "Sem Projeto";

            List<Point> points = new ArrayList<>();

            // ── Point principal ───────────────────────────────────────
            Map<String, Object> prod = (Map<String, Object>)
                dados.getOrDefault("produtividade", Map.of());

            Point p = Point.measurement("session")
                .addTag("usuario",    truncate(usuario, 64))
                .addTag("session_id", sessionId)
                .addTag("projeto",    truncate(projeto, 64))
                .addField("descricao",           truncate(descricao, 255))
                .addField("duracao_segundos",     toLong(dados.get("duracao_segundos")))
                .addField("duracao_formatada",    (String) dados.getOrDefault("duracao_formatada","0m"))
                .addField("score_produtividade",  toDouble(prod.get("score")))
                .addField("total_interacoes",     toLong(prod.get("total_interacoes")))
                .time(fim, WritePrecision.NS);
            points.add(p);

            // ── Um Point por app ──────────────────────────────────────
            Map<String, Object> apps = (Map<String, Object>)
                dados.getOrDefault("aplicativos", Map.of());

            for (Map.Entry<String, Object> entry : apps.entrySet()) {
                String appName = entry.getKey();
                if (appName == null || appName.isBlank()) continue;
                Map<String, Object> aData = (Map<String, Object>) entry.getValue();
                Map<String, Object> inter = (Map<String, Object>)
                    aData.getOrDefault("interacoes", Map.of());

                Point pa = Point.measurement("app_usage")
                    .addTag("usuario",    truncate(usuario, 64))
                    .addTag("session_id", sessionId)
                    .addTag("app",        truncate(appName, 64))
                    .addTag("categoria",  (String) aData.getOrDefault("categoria", "outros"))
                    .addField("tempo_segundos", toLong(aData.get("tempo_segundos")))
                    .addField("teclas",         toLong(inter.get("teclas")))
                    .addField("cliques",        toLong(inter.get("cliques")))
                    .time(fim, WritePrecision.NS);
                points.add(pa);
            }

            LOG.info("[DB] Enviando " + points.size() + " point(s)  session=" + sessionId);
            writeApi.writePoints(points);
            LOG.info("[DB] Sync OK  session=" + sessionId);
            return true;

        } catch (InfluxException e) {
            LOG.log(Level.WARNING, "[DB] InfluxException ao salvar: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[DB] Erro inesperado ao salvar", e);
            return false;
        }
    }

    // ── Leitura ───────────────────────────────────────────────────────

    public List<Map<String, Object>> buscarHistorico(Instant inicio, Instant fim) {
        if (!conectado) { LOG.warning("[DB] buscarHistorico: não conectado."); return List.of(); }

        try {
            String flux = String.format("""
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r._measurement == "session")
                  |> pivot(rowKey: ["_time","session_id","projeto","usuario"],
                           columnKey: ["_field"],
                           valueColumn: "_value")
                  |> sort(columns: ["_time"], desc: true)
                """,
                AppConfig.INFLUX_BUCKET,
                inicio.toString(),
                fim.toString()
            );

            List<Map<String, Object>> historico = new ArrayList<>();
            queryApi.query(flux).forEach(table ->
                table.getRecords().forEach(record -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Map<String, Object> vals = record.getValues();
                    row.put("session_id",          safeStr(vals.get("session_id")));
                    row.put("descricao",            safeStr(vals.get("descricao")));
                    row.put("projeto",              safeStr(vals.getOrDefault("projeto","Sem Projeto")));
                    row.put("usuario",              safeStr(vals.get("usuario")));
                    row.put("duracao_segundos",     toLong(vals.get("duracao_segundos")));
                    row.put("duracao_formatada",    safeStr(vals.get("duracao_formatada")));
                    row.put("score_produtividade",  toDouble(vals.get("score_produtividade")));
                    row.put("inicio",               record.getTime());
                    row.put("fim",                  record.getTime());
                    historico.add(row);
                })
            );

            LOG.info("[DB] Histórico: " + historico.size() + " registro(s)");
            return historico;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[DB] Erro na busca: " + e.getMessage(), e);
            return List.of();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String obterHostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "desconhecido"; }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    private static String safeStr(Object v) {
        return v == null ? "" : v.toString();
    }
}
