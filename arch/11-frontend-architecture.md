# Frontend Architecture

## Overview

The Orth frontend (`xxl-job-ui/`) is a standalone React SPA, fully decoupled from the Spring Boot admin backend. It communicates exclusively via REST API with JWT authentication.

**Tech Stack**: React 19, Ant Design 5, Zustand, TanStack React Query, Vite, TypeScript

## Directory Structure

```
xxl-job-ui/src/
├── api/            # Modular API clients (one file per domain)
├── components/     # Layout (AppLayout, AppSider, AppHeader), Guards
├── hooks/          # Custom hooks (pagination, permissions, responsive, enums, theme)
├── pages/          # Page components with co-located sub-components
├── store/          # Zustand stores (auth, theme, config)
├── theme/          # Ant Design theme tokens, color palettes
├── types/          # TypeScript interfaces (mirrors backend DTOs)
├── utils/          # Constants, date helpers
├── App.tsx         # Route definitions
└── main.tsx        # App bootstrap
```

## Pages & Routes

| Route | Page | Auth | Admin Only |
|-------|------|------|-----------|
| `/login` | Login | No | - |
| `/dashboard` | Dashboard (charts & stats) | Yes | No |
| `/jobs` | Job Management (CRUD, trigger, batch) | Yes | No |
| `/jobs/:jobId/code` | GLUE Code Editor (CodeMirror) | Yes | No |
| `/logs` | Execution Logs | Yes | No |
| `/executor-groups` | Executor Groups | Yes | Yes |
| `/users` | User Management | Yes | Yes |

All pages are lazy-loaded with Suspense fallback. Two route guards: `AuthGuard` (JWT check) and `AdminGuard` (admin role check).

## State Management

Three layers, each with a clear responsibility:

| Layer | Tool | Scope |
|-------|------|-------|
| **Global state** | Zustand (with `persist`) | Auth (tokens, user), theme mode, app config (enums, menus) |
| **Server state** | TanStack React Query | API data caching, auto-refetch, mutations |
| **Local state** | React `useState` | Form inputs, modal visibility, page filters |

## API & Authentication

API clients in `src/api/` share an Axios instance with a JWT interceptor. On 401, the interceptor queues concurrent requests, refreshes the token, then replays. If refresh fails, it redirects to `/login`.

## Theming

Three modes: **Light**, **Dark**, **System** (auto-follows OS preference). Ant Design algorithm switching + CSS `data-theme` attribute for non-Ant components. Custom color palettes for ECharts charts.

## Responsive Design

Mobile breakpoint at 768px. Sidebar collapses to drawer, table columns are conditionally hidden, form layouts switch between `inline` and `vertical`.

## Key UI Patterns

### Log Status Column

Trigger and Handle status codes are merged into a single **Status** tag showing `{status} {triggerCode}/{handleCode}`. Clicking the tag opens a popover with the raw `trigger_msg` and `handle_msg` for debugging. See [05-log-management.md](./05-log-management.md#log-status-reference) for the full status mapping.

### Fuzzy Job Search

The Job filter on the Logs page supports fuzzy search by job description or ID, with debounced backend queries.

### Fork SuperTask

Batch copy a SuperTask's sub-tasks to new schedule times. See [08-supertask-pattern.md](./08-supertask-pattern.md).

## Deployment

The production build (`npm run build`) outputs to `dist/`, which is copied into the admin Docker image. Nginx as sole entry point is planned but not yet in use.
