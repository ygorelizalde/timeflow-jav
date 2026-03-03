package com.timeflow.launcher;

import org.update4j.Configuration;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TimeFlowLauncher {

    private static final String CONFIG_URL =
        "https://github.com/ygorelizalde/timeflow-jav/releases/download/v2.1.0/config.xml";

    public static void main(String[] args) {
        System.out.println("[Launcher] Time Flow v2.0 iniciando...");

        Configuration config = null;

        // 1. Verifica e baixa atualizações da nuvem
        if (shouldCheckForUpdate(args)) {
            try {
                System.out.println("[Launcher] Verificando atualizacoes na nuvem...");
                URL url = new URL(CONFIG_URL);
                try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
                    config = Configuration.read(reader);
                }

                if (config.requiresUpdate()) {
                    System.out.println("[Launcher] Nova versao encontrada! Baixando arquivos...");
                    config.update();
                    System.out.println("[Launcher] Atualizacao concluida com sucesso.");
                } else {
                    System.out.println("[Launcher] O sistema ja esta na versao mais recente.");
                }
                
            } catch (Exception e) {
                System.out.println("[Launcher] Update ignorado (sem internet ou erro): " + e.getMessage());
            }
        }

        // 2. Inicia o aplicativo
        try {
            if (config != null) {
                // Abre a versão que acabou de ser baixada e salva na pasta do usuário!
                config.launch(); 
            } else {
                // Fallback: Se estiver sem internet, tenta abrir a versão local
                com.timeflow.app.TimeFlowApp.launch(com.timeflow.app.TimeFlowApp.class, args);
            }
        } catch (Exception e) {
            System.err.println("[Erro] Falha ao iniciar a interface grafica: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean shouldCheckForUpdate(String[] args) {
        for (String a : args) {
            if ("--no-update".equals(a)) return false;
        }
        return true;
    }
}