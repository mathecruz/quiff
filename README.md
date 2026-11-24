# quiff

Local GitHub-style compare page for any Git repository. No GitHub, no push.

- Pick **base** and **compare** refs (branches, remote branches, tags, or *Working tree*).
- *Working tree* = current branch + staged, unstaged and untracked files.
- Three-dot compare, like GitHub: shows what compare adds since it forked from base.
- Unified diff, file tree with filter, "Viewed" checkboxes, hide whitespace, commits tab.
- Listens on `localhost:8790` only.

## Run

```bash
cd /path/to/any/repo
~/workspace/quiff/bin/quiff          # base = main/master
~/workspace/quiff/bin/quiff develop  # explicit base
```

The first run builds the jar (`mvn package`). Port: `QUIFF_PORT=9000`.

As a Git alias (`git quiff`):

```bash
git config --global alias.quiff '!~/workspace/quiff/bin/quiff'
```

## Develop

```bash
mvn quarkus:dev -Dquiff.repo=/path/to/repo
mvn package   # runs the tests against a temporary Git repository
```
