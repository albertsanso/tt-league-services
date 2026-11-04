---
name: analyse-data-update-orchestrator-and-build-backlog-items
description: Analyse the data update orchestrator and build backlog items for the new Pipeline orchestrator module for results and match data ingestion.
---

# Summary
Analyze the data update orchestrator proposal and build backlog items for the new Pipeline orchestrator module for results and match data ingestion.

# Description
Based on `docs/analysis/update-data-pipeline-orchestrator.md` proposal, and current implementation of `tt-data-league-*` and `tt-legue-ingest` modules, 
we need to analyze the data update orchestrator and build backlog items for the new Pipeline orchestrator module for results and match data ingestion.

# Technical Analysis
The data update orchestrator proposal suggests new maven modules that will handle the ingestion of league results and match data from source files into the platform. 
This module will complement the existing Java import pipeline and will be designed to be used in conjunction with the platform's ingestion pipeline.

The new modules will be:
- `tt-league-pipeline-orchestrator-runtime`: 
  - Backend java springboot module that orchestrates the ingestion of league results and match data from source files into the platform.
  - Tech stack: Java 21, Spring Boot 3, Gradle 8, JUnit 5, Mockito, Testcontainers, PostgreSQL, ...
  - Exposes REST API endpoints for triggering ingestion and monitoring progress.
- `tt-league-pipeline-orchestrator-frontend`:
  - Frontend module that provides a user interface for monitoring and managing the ingestion process.
  - Tech stack: React, TypeScript, Material UI, Jest, React Testing Library, ...

Consider if other helper modules around the orchestrator are needed, like a common module for shared logic and utilities.

# Goal
Build backlog items for the new Pipeline orchestrator module for results and match data ingestion, based on the analysis of the data update orchestrator proposal and current implementation of `tt-data-league-*` and `tt-legue-ingest` modules.
