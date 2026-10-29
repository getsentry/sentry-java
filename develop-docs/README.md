# Develop Docs

This folder holds internal developer documentation for the Sentry Java/Android SDK:
architecture notes, feature deep-dives, design decisions, and cross-module concepts
that don't belong in the public [Sentry docs](https://docs.sentry.io) or in inline
code comments.

If you are documenting **how** or **why** something works for the people who maintain
this SDK, it goes here. If you are documenting **how to use** the SDK for end users,
it belongs in the public docs instead.

## Rules

These rules keep the docs consistent, easy to navigate, and easy to grep.

### Directory structure

Documents live in **subdirectories**, one level per level of grouping. Directories are
cheap: reach for a new one as soon as a topic has more than one document, or as soon as you
can name the group.

Every document sits under one of these top-level categories:

- `general/` — cross-cutting topics (e.g. `general/architecture.md`, `general/pipeline.md`)
- `feature/` — a specific SDK feature (e.g. `feature/errors/`, `feature/profiling/`)
- `integration/` — a specific integration or module (e.g. `integration/opentelemetry/`, `integration/spring/`)
- `platform/` — platform-specific concerns (e.g. `platform/android/`, `platform/jvm/`)
- `process/` — team processes and workflows (e.g. `process/release.md`)

Add a new category only when an existing one clearly does not fit, and keep the list above
up to date.

Below the category, nest by topic and then by sub-topic. A fully grown feature might look
like this:

```text
develop-docs/
  README.md
  general/
    pipeline.md
  feature/
    profiling/
      overview.md
      perfetto.md
      anr.md
      symbolication/
        deobfuscation.md
```

- Give a directory an `overview.md` once it holds several documents, and link to its
  siblings from there.
- Do not create a directory that will only ever hold one document — put the document
  directly in the category (`general/pipeline.md`, not `general/pipeline/pipeline.md`).

### File naming

- File names are **lowercase**, except for this `README.md`, which GitHub renders as the
  folder's landing page.
- Use **dashes** (`-`) as separators, never underscores or spaces. For example, use
  `session-replay.md`, not `session_replay.md` or `Session Replay.md`.
- Use the `.md` extension for all text documents.
- **Do not repeat the path in the file name.** The directories carry the namespace, so the
  file name only needs the part that distinguishes it from its siblings:
  `feature/profiling/perfetto.md`, not `feature/profiling/perfetto-profiling.md`.
- Choose short, descriptive names (`feature/replay/masking.md`, not
  `feature/replay/how-masking-works.md`).

### Images and other assets

- When a document embeds images (or other binary assets), store them in an **`assets/`
  folder next to the document**. Documents in the same directory share it:

  ```text
  develop-docs/
    feature/
      profiling/
        perfetto.md
        assets/
          pipeline.png
          overview.svg
  ```

- Reference assets with **relative paths**: `![Profiling pipeline](assets/pipeline.png)`.
- Asset file names follow the same rules as documents: lowercase, dashes, descriptive.
- Prefer **vector formats** (SVG) for diagrams and screenshots where practical
- Prefer **Mermaid** over a static image whenever a diagram can be expressed as one
  (see below) — it lives in the document, is versioned as text, and is easy to update.

### Writing style

- Write in the **present tense** and the **active voice**. Describe how the system
  behaves now ("The transport retries failed envelopes"), not how it will or did behave.
  This way there's no need to update the docs once a feature ships.
- Keep one **top-level `# ` heading** per document (the title), and nest sections with
  `##`, `###`, etc. Do not skip heading levels.
- Keep documents focused on a **single topic**. Split large topics into several documents
  in a shared directory and link between them rather than growing one giant file.
- Use fenced **code blocks with a language identifier** (```kotlin `,
  ` ```bash `) so syntax highlighting works.
- Prefer Kotlin snippets over Java.
- When referencing code, link to the file with a **relative path** (e.g.
  `../../../sentry/src/main/java/io/sentry/Sentry.java`) rather than pasting large excerpts
  that fall out of date. Count the `../` from the document's own directory.
- Avoid pinning content to a specific SDK version or date unless it is genuinely
  version-specific; keep docs evergreen.
- Cross-link related documents with relative links (e.g.
  `[the ingestion pipeline](../../general/pipeline.md)`).

### Structuring a feature document

Most feature documents answer the same four questions, and following that order makes them
easier to compare and to keep current:

1. **Surface area** — where and when the SDK collects the data.
2. **Collection** — how the SDK collects it.
3. **Format** — what the collected data looks like on the wire.
4. **Pipeline** — how the backend ingests, stores, and serves it.

Do not restate (4) in every document. Describe the shared path once in
[general/pipeline.md](general/pipeline.md) and cover only the deviations a feature
introduces. Omit any of the four that a feature does not have, and keep each as high-level
as the topic allows so the document stays true for longer.

### Diagrams with Mermaid

- Prefer [Mermaid](https://mermaid.js.org/) for diagrams. It renders directly on GitHub
  and lives in the document as text, so it versions and reviews like code.
- Embed a Mermaid diagram in a fenced block tagged `mermaid`:

  ````markdown
  ```mermaid
  flowchart LR
    Event[SentryEvent] --> Processor[EventProcessors]
    Processor --> Transport
    Transport --> Sentry[(Sentry)]
  ```
  ````

- For complex diagrams, include a link to the [Mermaid Live Editor](https://mermaid.live/)
  so reviewers can iterate quickly.
- Fall back to static images (stored per the asset rules above) if mermaid is not practicable.

## Adding a new document

1. Pick the right top-level category (or introduce a new one and document it above).
2. Pick or create the topic directory below it.
3. Create the document, naming it for what distinguishes it from its siblings.
4. If the directory now holds several documents, add or update its `overview.md`.
5. If the document embeds assets, put them in an `assets/` folder next to it.
