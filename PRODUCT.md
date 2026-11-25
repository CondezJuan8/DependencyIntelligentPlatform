# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Engineering and security teams use the platform together to understand the dependencies in their repositories, review published security advisories, and coordinate follow-up work.

## Product Purpose

Dependency Intelligence imports GitHub repositories or local project folders, runs manual Maven and npm dependency analysis, presents inventory and OSV evidence, and helps teams review and report on the results.

## Positioning

No exclusive market position has been confirmed. Do not invent customer claims, benchmarks, certifications, or comparative claims.

## Operating Context

The product is a responsive web application. Users work across organizations and repositories with role and repository scoped permissions. Repository analysis is initiated manually. Results include provenance and coverage limits; OSV findings are evidence for review, not proof that code is exploitable or that a package is safe.

## Capabilities and Constraints

- Existing capabilities include account access, GitHub and local repository intake, Maven/npm analysis, dependency inventory, OSV review, organization and repository access management, reports, technical documentation, and the Nexo assistant.
- Keep existing Angular 22, Angular Material, CDK, TypeScript, RxJS, and Sass architecture.
- Preserve API contracts, authentication, authorization, real data, current navigation, and all implemented actions.
- Do not add background upstream release checks, fake metrics, mock records, or capabilities that are not backed by the current product.
- The current local Compose database has no accounts, organizations, or repositories. A safe first administrator bootstrap is a separate pending operational step.

## Brand Commitments

- Product name: Dependency Intelligence.
- On 24 November 2026, the user authorized a new visual direction and palette. The Field Atlas system in `DESIGN.md` and `frontend/src/theme/` supersedes the historical palette in `docs/DISENO_FRONTEND.md`.
- Keep the recognizable geometry of the local product mark and the included IBM Plex Sans assets.
- The user selected dark mode as the default while retaining the existing theme switch.
- Linear, Vercel, Snyk, and GitHub Advanced Security are quality references only. Do not reproduce their layouts or brand assets.

## Evidence on Hand

The application contains real API integrations and explicit empty, loading, error, and partial coverage states. The current Compose data store is empty, so no authenticated product records or live security metrics are available for visual review.

## Product Principles

- Put dependency evidence and its provenance before decoration.
- Make coverage, freshness, and uncertainty visible without overstating what a scan proves.
- Keep engineering and security workflows understandable in the same workspace.
- Preserve access boundaries and use only data the signed in user may see.
- Keep the interface useful when a repository, scan, document, or OSV result is missing.

## Accessibility & Inclusion

Preserve keyboard operation, visible focus, semantic labels, screen reader status/error feedback, reduced motion, responsive reflow, and accessible authentication including password managers and paste.
