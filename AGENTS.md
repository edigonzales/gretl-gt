# Repository Guidelines

This repository contains the `gretl-gt` Gradle plugin for reproducible geoprocessing with GeoTools. The rules in this file apply to the entire repository.

## Design & Coding Principles
- Target **Java 11** via the Gradle toolchain; do not introduce features requiring newer language levels.
- Prefer immutable data structures and explicit task inputs/outputs so Gradle can reason about task caching.
- Keep Gradle task logic small and focused. Extract reusable utilities into `src/main/java/ch/so/agi/gretlgt/internal` and keep plugin entry points slim.
- When touching task implementations, document behavior and parameters with Javadoc, including coordinate reference system expectations and side effects.
- Use descriptive Gradle task names and strongly type task properties (e.g., `RegularFileProperty`, `DirectoryProperty`). Avoid raw `File` references.

## Testing Expectations
- Add or update **unit tests** under `src/test` for pure logic and **functional tests** under `src/functionalTest` when behavior spans Gradle builds.
- Ensure `./gradlew check` remains green. If a faster subset is enough during development, run it first but execute the full `check` task before you finish.
- For geospatial operations, include representative fixtures with clear CRS metadata; prefer tiny rasters/vectors to keep the repo light.

## Dependency & Build Hygiene
- Update GeoTools dependencies consistently—if you change one `gt-*` module version, update the shared version constant.
- Keep plugin metadata (ID, display name, description) aligned between `build.gradle` and documentation.
- When adding new tasks or extensions, register them in `GretlGtPlugin` and document them in the README.

## Documentation & Samples
- Update `README.md` whenever you add user-facing tasks, options, or configuration flags. Include concise usage snippets and mention required spatial references or projections.
- Prefer Markdown tables or bullet lists for enumerating task parameters. Reference GeoTools classes or CRS codes explicitly where helpful.

## File Organization
- Place non-test sample data under `src/functionalTest/resources/samples` unless a different location is required.
- Keep package names under `ch.so.agi.gretlgt` or its `internal` subpackages; avoid introducing parallel hierarchies.

Adhering to these guidelines keeps the plugin reliable and maintainable for downstream Gradle builds.
