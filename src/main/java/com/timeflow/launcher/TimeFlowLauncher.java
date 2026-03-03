package com.timeflow.launcher;

import org.update4j.Configuration;
import org.update4j.UpdateOptions;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TimeFlowLauncher — entry point separado do módulo de lógica.
 *
 * Responsabilidades:
 *  1. Verificar se há atualização disponível via Update4j
 *  2. Baixar e instalar o novo timeflow-app.jar se necessário
 *  3. Inicializar TimeFlowApp normalmente
 *
 * Este arquivo NUNCA é atualizado automaticamente (é o bootstrap).
 * Apenas o timeflow-app.jar é substituído nas atualizações.
 */
public class TimeFlowLauncher {

    // Caminho local do XML de configuração Update4j (cacheado)
    private static final Path CONFIG_CACHE =
        Paths.get(System.getProperty("user.home"), ".timeflow", "update4j-config.xml");

    public static void main(String[] args) {
        System.out.println("[Launcher] Time Flow v2.0 iniciando...");

        if (shouldCheckForUpdate(args)) {
            checkAndUpdate();
        }

        // Inicia a aplicação JavaFX
        com.timeflow.app.TimeFlowApp.launch(com.timeflow.app.TimeFlowApp.class, args);
    }

    // ── Update4j ──────────────────────────────────────────────────────

    private static boolean shouldCheckForUpdate(String[] args) {
        // Pule update com argumento --no-update (útil em dev)
        for (String a : args) {
            if ("--no-update".equals(a)) return false;
        }
        return true;
    }

    private static void checkAndUpdate() {
        try {
            System.out.println("[Launcher] Verificando atualizações...");

            // Tenta buscar configuração remota
            String remoteUrl = com.timeflow.config.AppConfig.UPDATE4J_CONFIG_URL;
            Configuration config;

            try (Reader reader = new InputStreamReader(new URL(remoteUrl).openStream())) {
                config = Configuration.read(reader);
                // Persiste config localmente para uso offline
                Files.createDirectories(CONFIG_CACHE.getParent());
                config.write(Files.newBufferedWriter(CONFIG_CACHE));
            } catch (Exception networkEx) {
                System.out.println("[Launcher] Sem conexão para update, usando config local.");
                if (Files.exists(CONFIG_CACHE)) {
                    try (Reader r = Files.newBufferedReader(CONFIG_CACHE)) {
                        config = Configuration.read(r);
                    }
                } else {
                    System.out.println("[Launcher] Nenhuma config local encontrada, pulando update.");
                    return;
                }
            }

            if (config.requiresUpdate()) {
                System.out.println("[Launcher] Atualização disponível. Baixando...");
                config.update(UpdateOptions.archive(
                    Paths.get(System.getProperty("user.home"), ".timeflow", "updates")
                ));
                System.out.println("[Launcher] Atualização aplicada. Reinicie o app.");
                // Em produção: exibir diálogo JavaFX pedindo para reiniciar
            } else {
                System.out.println("[Launcher] Aplicação já está na versão mais recente.");
            }

        } catch (Exception e) {
            // Update não é crítico — app continua normalmente
            System.out.println("[Launcher] Update ignorado: " + e.getMessage());
        }
    }
}
