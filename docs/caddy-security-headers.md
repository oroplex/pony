# Recommended Caddy security headers

This repository does not ship a Caddyfile. Apply these headers on the VPS that
fronts `relay.pony.karlmagendavid.com` and `download.pony.karlmagendavid.com`.
They are a recommendation only — this file does not change the Node relay.

```caddy
header {
	Strict-Transport-Security "max-age=31536000; includeSubDomains"
	X-Content-Type-Options "nosniff"
	X-Frame-Options "DENY"
	Content-Security-Policy "frame-ancestors 'none'"
	Referrer-Policy "no-referrer"
}
```

Keep `Referrer-Policy: no-referrer` on `/pair` so a leftover query-string pairing
link does not leak its token via `Referer`. As of 0.6.5 the hand-off page puts
pairing fields in the URL fragment (`#v=…&token=…`) instead of the query.

Restrict relay CORS to the origins you actually serve. The Node process itself
still answers `GET /health`, `POST /pair`, and `/ws`.
