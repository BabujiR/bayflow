# Build context: repository root   (docker build -f frontend/Dockerfile .)
# Needs frontend/ (the app) and infra/nginx/ (the server config).

FROM node:22-alpine AS build
WORKDIR /app
RUN npm install -g pnpm@10
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY frontend/ ./
# Workshops live at <slug>.<base domain>; the bare base domain is the public sign-up site.
ARG VITE_BASE_DOMAIN=localhost
ENV VITE_BASE_DOMAIN=${VITE_BASE_DOMAIN}
RUN pnpm build

# nginx running as a non-root user (101), listening on 8080. Built on the official nginx image
# (patched within a day of each nginx release; nginxinc/nginx-unprivileged stopped at 1.30.6),
# made unprivileged here the way nginx-unprivileged does it. 1.30 is the stable branch: CD pulls
# the latest 1.30.x on every build.
FROM nginx:1.30-alpine
# Security fixes for Alpine packages released since the base image was built (CD passes a new
# APK_REFRESH on every run, so the build cache never keeps an old package set). Then the non-root
# setup: no "user" directive (we start as 101), the pid file and temp files where 101 can write,
# and /etc/nginx writable for the entrypoint, which writes conf.d from /etc/nginx/templates.
ARG APK_REFRESH=0
RUN echo "packages refreshed: ${APK_REFRESH}" && apk upgrade --no-cache \
    && sed -i '/^user /d' /etc/nginx/nginx.conf \
    && sed -i 's,/var/run/nginx.pid,/tmp/nginx.pid,; s,/run/nginx.pid,/tmp/nginx.pid,' /etc/nginx/nginx.conf \
    && sed -i '/^http {/a \    client_body_temp_path /tmp/client_temp;\n    proxy_temp_path /tmp/proxy_temp;\n    fastcgi_temp_path /tmp/fastcgi_temp;\n    uwsgi_temp_path /tmp/uwsgi_temp;\n    scgi_temp_path /tmp/scgi_temp;' /etc/nginx/nginx.conf \
    && chown -R 101:0 /etc/nginx /var/cache/nginx \
    && chmod -R g+w /etc/nginx /var/cache/nginx
USER 101
ENV AUTH_UPSTREAM=auth-service:8083 \
    APPOINTMENT_UPSTREAM=appointment-service:8081 \
    WORKSHOP_UPSTREAM=workshop-service:8082 \
    NOTIFICATION_UPSTREAM=notification-service:8084
COPY infra/nginx/snippets/ /etc/nginx/snippets/
COPY infra/nginx/templates/ /etc/nginx/templates/
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
