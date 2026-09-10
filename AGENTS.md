# AGENTS.md

## Project

**Wallet Ledger** backend service — Java 17+ / Spring Boot 3.x / relational database.
Build it as a production-minded service: safe money movement, permanent ledger of every change,
correct under concurrency and repeated requests.

## Before you start any work — read these

This repo uses local files that are **not committed to GitHub**. Read them before every task:

- `.context/requirements.md` — the confidential take-home assignment (the spec).
- `.context/personal.md` — the author's background, learning goals, and how to teach while implementing.

> These files are gitignored and exist only on this machine so you can read them. They must **never**
> be committed, quoted, or pasted into GitHub issues, commits, PRs, or the README. The submitted repo
> must look like a normal, self-contained project.

## Agent skills

### Issue tracker

Issues and specs live as markdown under `.scratch/<feature-slug>/` and **are committed to GitHub**
so reviewers can see how the work is divided. One feature per directory; one file per ticket at
`.scratch/<feature-slug>/issues/NN-<slug>.md`. See `docs/agents/issue-tracker.md`.

### Triage labels

Open issues carry a `Status:` line using the canonical roles. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context repo: `CONTEXT.md` at the root plus `docs/adr/` for decisions (created lazily when
domain terms or ADRs actually get resolved). Use the vocabulary defined there.

## Working conventions

- The **mandatory capabilities** come first; extras only after the core is solid and tested.
- Verify work compiles and tests pass before declaring a task done.
- The **README is the primary reviewer-facing artifact** — invest in its five required sections.
- Teach Java/Spring concepts while implementing; bridge from C# where the author has prior knowledge.