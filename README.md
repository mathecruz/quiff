<p align="center">
  <img src="docs/images/quiff.png" alt="quiff logo" width="240">
</p>

<p align="center">Local GitHub-style compare page for any Git repository. No GitHub, no push.</p>

---

- Pick **base** and **compare** refs (branches, remote branches, tags, or *Working tree*).
- *Working tree* = current branch + staged, unstaged and untracked files.
- Three-dot compare, like GitHub: shows what compare adds since it forked from base.
- Unified diff, file tree with filter, "Viewed" checkboxes, hide whitespace, commits tab.
- Listens on `localhost:8790` only.

## Requirements

- Java 17 or newer
- Maven 3.9+
- Git
- `curl` and `open` (macOS) or `xdg-open` (Linux), used to open the browser

## Install

```bash
git clone https://github.com/mathecruz/quiff.git ~/workspace/quiff
cd ~/workspace/quiff
mvn package
```

`mvn package` builds `target/quarkus-app/quarkus-run.jar` and runs the tests. You can skip this step:
`bin/quiff` builds the jar on its first run if it is missing.

## Run

From inside any Git repository:

```bash
cd /path/to/any/repo
~/workspace/quiff/bin/quiff          # base = main/master
~/workspace/quiff/bin/quiff develop  # explicit base, compared against the working tree
```

quiff starts the server, opens `http://localhost:8790/` in your browser and keeps running until you press
`Ctrl+C`.

### Change the port

```bash
QUIFF_PORT=9000 ~/workspace/quiff/bin/quiff
```

### Use it as `git quiff`

```bash
git config --global alias.quiff '!~/workspace/quiff/bin/quiff'
```

Then, in any repository:

```bash
git quiff
git quiff develop
```

### Update

`bin/quiff` only builds the jar when it does not exist. After pulling new changes, rebuild it:

```bash
cd ~/workspace/quiff
git pull
mvn package
```

## Develop

```bash
mvn quarkus:dev -Dquiff.repo=/path/to/repo   # live reload, compares /path/to/repo
mvn package                                  # runs the tests against a temporary Git repository
```
