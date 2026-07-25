# Glide Dynamic Proxy Design

## Goal

Make poster-image requests use the same active in-app SOCKS endpoint as catalog and playback requests, while retaining direct image loading whenever the proxy is disconnected.

## Root Cause

`AppGraph` supplies a proxy-aware `UrlConnectionHttpFetcher` for catalog APIs, and `VideoPlaybackController` applies `ProxyConnectionPolicy` to its OkHttp client. `VideoListAdapter` and `SearchResultAdapter` load `coverUrl` directly through Glide's default network stack, so they bypass the active local SOCKS endpoint. The production catalog currently returns HTTPS image URLs at `static.tripdata.app`; this is not an Android cleartext-traffic issue.

## Design

Add Glide's official OkHttp integration and register an application-level Glide module. The module creates one OkHttp client whose `ProxySelector` obtains the current endpoint from `AppGraph.proxyManager.activeEndpoint` for each request:

- an active endpoint becomes a SOCKS proxy through `ProxyConnectionPolicy`;
- no active endpoint returns `Proxy.NO_PROXY`, preserving current direct loading;
- a failed proxy connection reports through the standard client callback and does not change proxy state.

The Glide module replaces the `GlideUrl` to `InputStream` network loader. Glide's existing string model loader then routes both homepage and search-result `coverUrl` values through this client, preserving the current adapters, image transforms, lifecycle handling, and disk/memory caches.

## Error Handling

The proxy selector always returns a non-empty proxy list. Invalid or unavailable images retain Glide's existing failure behavior; this change does not retry through direct networking when a configured proxy fails, since that would bypass the user's selected network route.

## Tests

Add pure unit tests for the selector:

- an active `LocalProxyEndpoint` selects a SOCKS proxy with the matching host and port;
- no endpoint selects `Proxy.NO_PROXY`;
- failed connection callbacks do not throw.

Run the focused unit tests, the full local test suite, and Android lint. Device verification covers a connected proxy, a changed selected node, and a disconnected proxy on both homepage and search-result posters.
