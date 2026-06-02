package io.vidocq.tools.arago.pins;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Best-effort, SSRF-hardened OpenGraph preview fetcher for URL pins (§4.4/§11 Phase 2). Fetches the page
 * server-side and extracts {@link OpenGraph} metadata. Every failure (block, timeout, non-200,
 * non-HTML, parse miss) yields {@link Optional#empty()} — the pin is simply created without a preview.
 *
 * <p>SSRF hardening (cf. {@link SsrfGuard}): http/https only; the host's resolved IPs are validated
 * before each request; redirects are followed <strong>manually</strong> ({@link HttpClient.Redirect#NEVER},
 * ≤ {@value #MAX_REDIRECTS} hops, each re-validated); a short timeout; the body is read capped; only
 * {@code text/html} is parsed. {@code arago.pins.preview.allow-private=true} relaxes the private-IP
 * block for tests only (a local server on 127.0.0.1) — production default is {@code false}.</p>
 *
 * <p>Known limitation: a TOCTOU/DNS-rebinding window remains (the client re-resolves DNS at send time);
 * acceptable for a best-effort preview, a connect-by-validated-IP is a later hardening.</p>
 */
@ApplicationScoped
public class OgPreviewFetcher {

    private static final System.Logger LOG = System.getLogger(OgPreviewFetcher.class.getName());
    private static final int MAX_REDIRECTS = 3;
    private static final int CACHE_MAX = 500;
    private static final OpenGraph.Preview EMPTY = new OpenGraph.Preview(null, null, null);

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** Best-effort cache (URL → preview, negatives included) — honours the spec's "cache", bounded. */
    private final Map<String, OpenGraph.Preview> cache = new ConcurrentHashMap<>();

    public Optional<OpenGraph.Preview> fetch(String rawUrl) {
        OpenGraph.Preview cached = cache.get(rawUrl);
        if (cached != null) {
            return cached.isEmpty() ? Optional.empty() : Optional.of(cached);
        }
        Optional<OpenGraph.Preview> result = doFetch(rawUrl);
        if (cache.size() < CACHE_MAX) {
            cache.put(rawUrl, result.orElse(EMPTY));
        }
        return result;
    }

    private Optional<OpenGraph.Preview> doFetch(String rawUrl) {
        boolean allowPrivate = configBool("arago.pins.preview.allow-private", false);
        long timeoutMs = configLong("arago.pins.preview.timeout-ms", 3000);
        int maxBytes = (int) configLong("arago.pins.preview.max-bytes", 262_144);
        try {
            URI uri = URI.create(rawUrl.trim());
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                String scheme = uri.getScheme();
                if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                    return Optional.empty();
                }
                String host = uri.getHost();
                if (host == null) {
                    return Optional.empty();
                }
                if (!allowPrivate && SsrfGuard.hostIsBlocked(host)) {
                    return Optional.empty();
                }
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Accept", "text/html")
                        .header("User-Agent", "Arago-link-preview")
                        .GET().build();
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                int status = resp.statusCode();
                if (status >= 300 && status < 400) {
                    String location = resp.headers().firstValue("Location").orElse(null);
                    resp.body().close();
                    if (location == null) {
                        return Optional.empty();
                    }
                    uri = uri.resolve(location); // resolve relative redirects; loop re-validates the target
                    continue;
                }
                if (status != 200) {
                    resp.body().close();
                    return Optional.empty();
                }
                String contentType = resp.headers().firstValue("Content-Type").orElse("");
                if (!contentType.toLowerCase().contains("text/html")) {
                    resp.body().close();
                    return Optional.empty();
                }
                String html = readBounded(resp.body(), maxBytes);
                OpenGraph.Preview preview = OpenGraph.parse(html);
                return preview.isEmpty() ? Optional.empty() : Optional.of(preview);
            }
            return Optional.empty(); // too many redirects
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "link preview fetch failed for " + rawUrl, e);
            return Optional.empty();
        }
    }

    private static String readBounded(InputStream in, int maxBytes) throws java.io.IOException {
        try (in) {
            return new String(in.readNBytes(maxBytes), StandardCharsets.UTF_8);
        }
    }

    private static boolean configBool(String key, boolean def) {
        return ConfigProvider.getConfig().getOptionalValue(key, Boolean.class).orElse(def);
    }

    private static long configLong(String key, long def) {
        return ConfigProvider.getConfig().getOptionalValue(key, Long.class).orElse(def);
    }
}
