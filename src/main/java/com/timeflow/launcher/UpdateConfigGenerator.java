package com.timeflow.launcher;

import org.update4j.Configuration;
import org.update4j.FileMetadata;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UpdateConfigGenerator {

    private static final String DOWNLOAD_URI = 
        "https://github.com/ygorelizalde/timeflow-jav/releases/download/v2.1.0/";

    public static void main(String[] args) {
        try {
            // Garante que estamos olhando para a pasta correta
            Path projectDir = Paths.get(System.getProperty("user.dir"));
            Path targetJar = projectDir.resolve("target").resolve("timeflow-app-2.0.0-shaded.jar");

            if (!Files.exists(targetJar)) {
                System.err.println("[Erro] Arquivo não encontrado em: " + targetJar.toAbsolutePath());
                System.err.println("Certifique-se de rodar 'mvn clean package' antes!");
                return;
            }

            System.out.println("[Gerador] Lendo JAR: " + targetJar.getFileName());

            Configuration config = Configuration.builder()
                .baseUri(DOWNLOAD_URI)
                .basePath("${user.home}/.timeflow/app")
                .property("default.launcher.main.class", "com.timeflow.app.TimeFlowApp")
                .file(FileMetadata.readFrom(targetJar.toAbsolutePath().toString())
                    .uri(targetJar.getFileName().toString()) 
                    .path("timeflow-app.jar") 
                    .classpath()) 
                .build();

            // Forçando o salvamento na pasta target do projeto
            Path outputXml = projectDir.resolve("target").resolve("config.xml");
            
            // Cria a pasta target caso ela não exista por algum motivo
            Files.createDirectories(outputXml.getParent());

            try (Writer out = Files.newBufferedWriter(outputXml)) {
                config.write(out);
            }

            System.out.println("====================================================");
            System.out.println("SUCESSO! Arquivo gerado em:");
            System.out.println(outputXml.toAbsolutePath());
            System.out.println("====================================================");

        } catch (Exception e) {
            System.err.println("[Erro] Falha ao gerar o XML: " + e.getMessage());
            e.printStackTrace();
        }
    }
}