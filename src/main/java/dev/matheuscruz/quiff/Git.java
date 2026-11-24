package dev.matheuscruz.quiff;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Git {

    private final Path repo;

    public Git(@ConfigProperty(name = "quiff.repo") Path repo) {
        this.repo = repo.toAbsolutePath().normalize();
    }

    public Path repo() {
        return repo;
    }

    public String run(String... args) {
        return run(Set.of(0), args);
    }

    /**
     * Runs git without a shell, so arguments are never interpreted. Some commands signal "differences found"
     * with a non-zero exit code (e.g. {@code diff --no-index} returns 1), hence {@code okExitCodes}.
     */
    public String run(Set<Integer> okExitCodes, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", repo.toString(), "-c", "core.quotePath=false"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).start();
            // Drain stderr concurrently so a chatty command cannot block on a full pipe.
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            String stdout = read(process.getInputStream());
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new GitException("git timed out: " + String.join(" ", args));
            }
            if (!okExitCodes.contains(process.exitValue())) {
                throw new GitException("git " + String.join(" ", args) + " failed: " + stderr.join().strip());
            }
            return stdout;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("Interrupted while running git");
        }
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class GitException extends RuntimeException {
        public GitException(String message) {
            super(message);
        }
    }
}
