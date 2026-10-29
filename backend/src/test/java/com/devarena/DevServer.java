package com.devarena;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;

/**
 * Runs DevArena locally with a private embedded PostgreSQL, so no database install is needed.
 * Data persists in {@code ../.data/pg} between runs. Test-scoped: never part of the deployable jar.
 *
 * <pre>mvn -q test-compile exec:java</pre>
 */
public final class DevServer {
    private DevServer() {}

    public static void main(String[] args) throws Exception {
        File dataDir = new File(System.getProperty("devarena.dataDir", "../.data/pg")).getAbsoluteFile();
        dataDir.mkdirs();
        int port = Integer.getInteger("devarena.pgPort", 5544);
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .setPort(port)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (Exception ignored) {
                // best effort
            }
        }));
        List<String> all = new ArrayList<>(List.of(args));
        all.add("--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"));
        all.add("--spring.datasource.username=postgres");
        all.add("--spring.datasource.password=postgres");
        SpringApplication.run(DevArenaApplication.class, all.toArray(String[]::new));
    }
}
