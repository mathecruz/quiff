package dev.matheuscruz.quiff;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.runtime.annotations.RegisterForReflection;

@Path("/api")
public class CompareResource {

    /** Pseudo-ref for uncommitted changes: staged, unstaged and untracked files on top of HEAD. */
    public static final String WORKTREE = "WORKTREE";

    // Refs come from the browser: refuse anything git could read as an option or a revision expression.
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/\\-]*");

    private static final String FIELD = "\u001f";
    private static final String RECORD = "\u001e";

    @Inject
    Git git;

    @GET
    @Path("/refs")
    public Refs refs() {
        String current = git.run("branch", "--show-current").strip();
        List<String> branches = lines(git.run("branch", "--format=%(refname:short)"));
        List<String> remotes = lines(git.run("branch", "-r", "--format=%(refname:short)")).stream()
                .filter(ref -> !ref.endsWith("/HEAD") && ref.contains("/"))
                .toList();
        List<String> tags = lines(git.run("tag", "--sort=-creatordate")).stream().limit(50).toList();

        String defaultBase = branches.contains("main") ? "main"
                : branches.contains("master") ? "master"
                        : current.isEmpty() ? "HEAD" : current;
        return new Refs(git.repo().getFileName().toString(), current.isEmpty() ? "HEAD" : current, defaultBase,
                branches, remotes, tags);
    }

    @GET
    @Path("/compare")
    public Comparison compare(@QueryParam("base") String base, @QueryParam("head") @DefaultValue(WORKTREE) String head,
            @QueryParam("w") @DefaultValue("false") boolean ignoreWhitespace) {
        String baseCommit = resolve(base);
        boolean worktree = WORKTREE.equals(head);
        String headCommit = resolve(worktree ? "HEAD" : head);
        // Like GitHub's three-dot compare: show what head adds since it forked from base.
        String mergeBase = git.run(Set.of(0, 1), "merge-base", baseCommit, headCommit).strip();
        if (mergeBase.isEmpty()) {
            throw new BadRequestException(base + " and " + head + " have no common history");
        }

        List<String> diffArgs = new ArrayList<>(List.of("diff", "--no-color", "--no-ext-diff", "-M"));
        if (ignoreWhitespace) {
            diffArgs.add("-w");
        }
        StringBuilder diff = new StringBuilder();
        if (worktree) {
            // A single commit argument diffs that commit against the working tree, staged changes included.
            diff.append(git.run(concat(diffArgs, mergeBase, "--")));
            for (String file : untrackedFiles()) {
                diff.append(git.run(Set.of(0, 1), concat(diffArgs, "--no-index", "--", "/dev/null", file)));
            }
        } else {
            diff.append(git.run(concat(diffArgs, mergeBase, headCommit, "--")));
        }

        return new Comparison(base, head, mergeBase, commits(mergeBase, headCommit), diff.toString());
    }

    /** Lines of a file on the compare side, used to expand the context around diff hunks. */
    @GET
    @Path("/file")
    public FileContent file(@QueryParam("ref") String ref, @QueryParam("path") String path) {
        java.nio.file.Path file = repoPath(path);
        String content;
        if (WORKTREE.equals(ref)) {
            content = readWorktreeFile(file);
        } else {
            String relative = git.repo().relativize(file).toString().replace('\\', '/');
            String blob = git.run(Set.of(0, 1, 128), "rev-parse", "--verify", "--quiet", "--end-of-options",
                    resolve(ref) + ":" + relative).strip();
            if (blob.isEmpty()) {
                throw new NotFoundException("No " + path + " in " + ref);
            }
            content = git.run("cat-file", "blob", blob);
        }
        // Split on \n only, like git numbers lines; a trailing newline does not start another line.
        String body = content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
        return new FileContent(content.isEmpty() ? List.of() : List.of(body.split("\n", -1)));
    }

    // Paths come from the browser too: only accept files inside the repository.
    private java.nio.file.Path repoPath(String path) {
        if (path == null || path.isBlank()) {
            throw new BadRequestException("Missing path");
        }
        try {
            java.nio.file.Path file = git.repo().resolve(path).normalize();
            if (!file.startsWith(git.repo()) || file.equals(git.repo())) {
                throw new BadRequestException("Path outside the repository: " + path);
            }
            return file;
        } catch (InvalidPathException e) {
            throw new BadRequestException("Invalid path: " + path);
        }
    }

    private String readWorktreeFile(java.nio.file.Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                throw new NotFoundException("No such file: " + git.repo().relativize(file));
            }
            // Symlinks could still point outside the repository.
            if (!file.toRealPath().startsWith(git.repo().toRealPath())) {
                throw new BadRequestException("Path outside the repository: " + git.repo().relativize(file));
            }
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String resolve(String ref) {
        if (ref == null || ref.isBlank() || !SAFE_REF.matcher(ref).matches()) {
            throw new BadRequestException("Invalid ref: " + ref);
        }
        String sha = git.run(Set.of(0, 1, 128), "rev-parse", "--verify", "--quiet", "--end-of-options",
                ref + "^{commit}").strip();
        if (sha.isEmpty()) {
            throw new BadRequestException("Unknown ref: " + ref);
        }
        return sha;
    }

    private List<String> untrackedFiles() {
        return Arrays.stream(git.run("ls-files", "--others", "--exclude-standard", "-z").split("\0"))
                .filter(file -> !file.isEmpty())
                .toList();
    }

    private List<Commit> commits(String from, String to) {
        String format = String.join(FIELD, "%H", "%h", "%an", "%aI", "%s") + RECORD;
        return Arrays.stream(git.run("log", "--reverse", "--format=" + format, from + ".." + to).split(RECORD))
                .map(String::strip)
                .filter(record -> !record.isEmpty())
                .map(record -> record.split(FIELD, -1))
                .map(f -> new Commit(f[0], f[1], f[2], f[3], f[4]))
                .toList();
    }

    private static List<String> lines(String output) {
        return output.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    private static String[] concat(List<String> head, String... tail) {
        List<String> all = new ArrayList<>(head);
        all.addAll(List.of(tail));
        return all.toArray(String[]::new);
    }

    @RegisterForReflection
    public record Refs(String repository, String current, String defaultBase, List<String> branches,
            List<String> remotes, List<String> tags) {
    }

    @RegisterForReflection
    public record Commit(String sha, String shortSha, String author, String date, String subject) {
    }

    @RegisterForReflection
    public record Comparison(String base, String head, String mergeBase, List<Commit> commits, String diff) {
    }

    @RegisterForReflection
    public record FileContent(List<String> lines) {
    }

    @Provider
    public static class GitExceptionMapper implements ExceptionMapper<Git.GitException> {
        @Override
        public Response toResponse(Git.GitException e) {
            return Response.status(500).type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();
        }
    }
}
