# METT HDR

Project name: METT HDR

Full name: METT Healthy Design Review

Chinese name: METT 健康设计分析平台

Project type: Independent SaaS platform

Backend direction: Java backend

Frontend direction: Web user frontend + Admin frontend

Architecture direction: Modular monolith first

Database direction: MySQL 8

Cache direction: Redis

File direction: Object storage + metadata-first file model

First professional module: Lighting Review MVP

## Project Boundary

METT HDR is not part of mett-admin.

METT HDR is not part of mett-website.

METT HDR integrates with the METT evaluation backend through controlled integration APIs.

## Repository Areas

- `hdr-api/`: Future Java backend REST API project.
- `hdr-web/`: Future METT HDR Web user frontend.
- `hdr-admin-web/`: Future METT HDR Admin Web.
- `docs/`: Planning and architecture documentation.

## Current Scope

This repository currently contains only the initial project skeleton, baseline documentation, ignore rules, and placeholder directories for future development.

No backend code, frontend code, authentication, membership, payment, product database, file upload, report generation, integration API client, or lighting module implementation is included in this PR.
