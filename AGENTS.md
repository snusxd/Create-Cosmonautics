# Agent Instructions

## Read First
- Read `AI_POLICY.md` before planning or changing files.
- Follow `CONTRIBUTING.md` for contribution, style, setup, and PR conventions.
- Human developers own feature direction and final review; inspect analogous code before adding new patterns.

## Package Manager
- Use the Gradle Wrapper with Java 21: `./gradlew <task>`.
- Main commands: `./gradlew build`, `./gradlew runClient`, `./gradlew runServer`, `./gradlew runData`.
- The project targets Minecraft 1.21.1 and NeoForge 21.1.x.

## File-Scoped Commands
- No file-scoped lint, format, or test tasks are configured.
- Focused Java compilation: `./gradlew compileJava`.
- Test task: `./gradlew test`.
- Full validation: `./gradlew build`.

## Project Layout
- Main Java code: `src/main/java/dev/devce/rocketnautics/`.
- Key areas: `api/`, `client/`, `compat/`, `content/`, `data/`, `lua/`, `ponder/`, `registry/`, and `server/`.
- `websnodelib/` is another library in the same Java source set; preserve its package boundary.
- Hand-authored resources: `src/main/resources/`; generated resources: `src/generated/resources/`.
- Do not edit generated resources by hand; update their providers/source data, run `./gradlew runData`, and inspect the diff.

## Key Conventions
- Reuse existing NeoForge, Create, Sable, and Registrate patterns; registrations normally belong in the appropriate `registry/` class and use `RocketNautics.getRegistrate()`.
- Keep client-only code under `client/`, compatibility integrations under `compat/`, and shared/server logic free of client-only imports.
- Keep changes scoped to the requested behavior. Add Javadoc for complex logic or public APIs as described in `CONTRIBUTING.md`.

## AI Policy
- Treat AI as an assistant: understand, manually review, adapt, compile, and test every AI-assisted change before submission.
- Disclose the AI tool/model and the extent of assistance in the PR description.
- Do not add AI-generated media or upload confidential data to closed AI services.
- Mark every AI-written or materially AI-assisted code block with a nearby comment, for example:
  `// AI-assisted: <tool/model>; <brief scope>.`
- Keep the marker truthful and local to the affected code; do not add markers to generated resources.

## Commit Attribution
- AI-authored commits MUST include the actual model identity and attribution byline:
  `Co-Authored-By: <agent model name> <attribution email>`
