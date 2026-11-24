package dev.matheuscruz.quiff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Repo layout: main has one commit; "feature" adds a commit on top; the working tree (on feature) has a
 * staged edit, an unstaged edit and an untracked file.
 */
public class TempRepoResource implements QuarkusTestResourceLifecycleManager {

    private Path repo;

    @Override
    public Map<String, String> start() {
        try {
            repo = Files.createTempDirectory("quiff-test");
            git("init", "-b", "main");
            Files.writeString(repo.resolve("app.txt"), "one\ntwo\n");
            git("add", ".");
            git("commit", "-m", "initial");

            git("switch", "-c", "feature");
            Files.writeString(repo.resolve("feature.txt"), "feature\n");
            git("add", ".");
            git("commit", "-m", "add feature");

            Files.writeString(repo.resolve("app.txt"), "one\ntwo staged\n");
            git("add", "app.txt");
            Files.writeString(repo.resolve("feature.txt"), "feature unstaged\n");
            Files.writeString(repo.resolve("new.txt"), "untracked\n");
            return Map.of("quiff.repo", repo.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void git(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-C", repo.toString(),
                "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.join(" ", command) + ": " + output);
        }
    }

    @Override
    public void stop() {
        if (repo != null) {
            try (var paths = Files.walk(repo)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
                // temp dir; best effort
            }
        }
    }
}
