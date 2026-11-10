# Build context: repository root   (docker build -f frontend/Dockerfile .)
# Needs frontend/ (the app) and infra/nginx/ (the server config).

FROM node:22-alpine AS build
WORKDIR /app
RUN npm install -g pnpm@10
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

# nginx running as a non-root user, listening on 8080
FROM nginxinc/nginx-unprivileged:stable-alpine
ENV AUTH_UPSTREAM=auth-service:8083 \
    APPOINTMENT_UPSTREAM=appointment-service:8081 \
    WORKSHOP_UPSTREAM=workshop-service:8082
COPY infra/nginx/snippets/ /etc/nginx/snippets/
COPY infra/nginx/templates/ /etc/nginx/templates/
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
