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
- A new local database uses the opt-in administrator bootstrap. Automated review uses a separate Compose project with its own database and mail service.

## Brand Commitments

- Product name: Dependency Intelligence.
- The November 25 visual package defines the Evidence Observatory direction in `DESIGN.md`. The interface uses ink surfaces in dark mode, warm surfaces in light mode, and amber for the primary action and attention states.
- Keep the local Dependency Intelligence mark and use Open Sans for the interface. Versions, hashes and paths use a monospace stack.
- The user selected dark mode as the default while retaining the existing theme switch.
- Linear, Vercel, Snyk, and GitHub Advanced Security are quality references only. Do not reproduce their layouts or brand assets.

## Evidence on Hand

The application contains real API integrations and explicit empty, loading, error, and partial coverage states. Product metrics come from the current authorized repositories and their saved analyses. The visual reference records are illustrative.

## Product Principles

- Put dependency evidence and its provenance before decoration.
- Make coverage, freshness, and uncertainty visible without overstating what a scan proves.
- Keep engineering and security workflows understandable in the same workspace.
- Preserve access boundaries and use only data the signed in user may see.
- Keep the interface useful when a repository, scan, document, or OSV result is missing.

## Accessibility & Inclusion

Preserve keyboard operation, visible focus, semantic labels, screen reader status/error feedback, reduced motion, responsive reflow, and accessible authentication including password managers and paste.
