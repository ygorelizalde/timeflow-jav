package com.timeflow.config;

/**
 * AppConfig — credenciais e parâmetros do app.
 *
 * ⚠️  Em produção: carregue tokens via variável de ambiente.
 *     Nunca commite tokens em repositórios públicos.
 */
public final class AppConfig {

    private AppConfig() {}

    // ── InfluxDB Cloud ────────────────────────────────────────────────
    public static final String INFLUX_URL    = "https://us-east-1-1.aws.cloud2.influxdata.com";
    public static final String INFLUX_TOKEN  = System.getenv().getOrDefault(
            "TIMEFLOW_INFLUX_TOKEN",
            "3GthEe_SQy5nDZlyJZel86tCoiUvsJiRlRz61HT0G_MhwyaXcZm1u-tsORGoxhgTTFCu46YBpkO4pOOS72IjVQ=="
    );
    public static final String INFLUX_ORG    = "ygorelizald@gmail.com";
    public static final String INFLUX_BUCKET = "Armazenamento";

    // ── Supabase ──────────────────────────────────────────────────────
    // Project URL: Supabase → Settings → API → Project URL
    public static final String SUPABASE_URL      = System.getenv().getOrDefault(
            "TIMEFLOW_SUPABASE_URL",
            "https://puzasxhwfiwmehrnvgwg.supabase.co"
    );
    // Anon Key: Supabase → Settings → API → anon public
    public static final String SUPABASE_ANON_KEY = System.getenv().getOrDefault(
            "TIMEFLOW_SUPABASE_KEY",
            "sb_publishable_Ek-6mlhxiu1JTHHarFoUjw_0vZw_b4g"
    );

    // ── Slack ─────────────────────────────────────────────────────────
    // Para ativar: crie um Incoming Webhook em api.slack.com/apps
    public static final String  SLACK_WEBHOOK_URL = System.getenv().getOrDefault(
            "TIMEFLOW_SLACK_WEBHOOK",
            ""   // deixe vazio para desativar
    );
    public static final boolean SLACK_ENABLED = !SLACK_WEBHOOK_URL.isEmpty();

    // ── Sync ──────────────────────────────────────────────────────────
    public static final int SYNC_INTERVAL_SECONDS  = 30;
    public static final int IDLE_THRESHOLD_SECONDS = 120;

    // ── Update4j ──────────────────────────────────────────────────────
    public static final String UPDATE4J_CONFIG_URL =
            "https://sua-cdn.com/timeflow/update4j-config.xml";

    // ── Categorias de apps ────────────────────────────────────────────
    public static final java.util.Map<String, java.util.List<String>> APP_CATEGORIES =
        java.util.Map.of(
            "produtividade",   java.util.List.of("word","excel","powerpoint","notion","obsidian","vscode","pycharm","sublime","code","cursor","idea"),
            "comunicacao",     java.util.List.of("outlook","gmail","teams","slack","discord","whatsapp","telegram"),
            "navegacao",       java.util.List.of("chrome","edge","firefox","brave","opera"),
            "design",          java.util.List.of("photoshop","illustrator","figma","canva","gimp","blender"),
            "entretenimento",  java.util.List.of("spotify","youtube","netflix","steam","twitch")
        );
}