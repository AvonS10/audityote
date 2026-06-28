# syntax=docker/dockerfile:1
# ControlMap frontend — multi-stage build: compile the Vite SPA, then serve the static bundle via nginx.

# --- build stage ---
FROM node:22-alpine AS build
WORKDIR /app
# Playwright is a dev-only screenshot helper — never download its browser during the image build.
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- runtime stage ---
FROM nginx:1.27-alpine AS runtime
# SPA routing fallback + same-origin /api proxy to the backend.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
