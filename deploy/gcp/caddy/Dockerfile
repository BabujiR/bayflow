# Caddy with the Cloudflare DNS module, needed for the wildcard certificate (*.<domain>):
# Let's Encrypt only issues wildcards through a DNS challenge, and the domain's DNS is at
# Cloudflare. Built from Caddy's official images; nothing third-party.
FROM caddy:2.11-builder AS build
RUN xcaddy build --with github.com/caddy-dns/cloudflare

FROM caddy:2.11
COPY --from=build /usr/bin/caddy /usr/bin/caddy
