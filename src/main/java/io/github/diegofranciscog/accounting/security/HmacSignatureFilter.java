package io.github.diegofranciscog.accounting.security;

import io.github.diegofranciscog.accounting.config.IntegrationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Authenticates webhook calls from facturador-sri and nomina-ec.
 * Signature: {@code X-Signature: sha256=hex(HMAC_SHA256(secret, X-Timestamp + "." + body))}.
 * The timestamp window blocks old replays, and the event id makes any replay inside the window idempotent.
 */
public class HmacSignatureFilter extends OncePerRequestFilter {

    public static final String PATH_PREFIX = "/api/v1/integrations/";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String ROLE = "ROLE_INTEGRATION";
    static final int MAX_BODY_BYTES = 64 * 1024;

    private final byte[] secret;
    private final IntegrationProperties properties;
    private final ProblemResponseWriter problems;
    private final Clock clock;

    public HmacSignatureFilter(IntegrationProperties properties, ProblemResponseWriter problems, Clock clock) {
        this.secret = properties.hmacSecret().getBytes(StandardCharsets.UTF_8);
        this.properties = properties;
        this.problems = problems;
        this.clock = clock;
    }

    /** Signature a client must send; also used by tests and the example in docs/. */
    public static String sign(byte[] secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC no disponible", e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            problems.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Payload demasiado grande",
                    "El evento supera 64 KB");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            problems.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Payload demasiado grande",
                    "El evento supera 64 KB");
            return;
        }
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);
        if (!validTimestamp(timestamp) || signature == null || !constantTimeEquals(sign(secret, timestamp, body), signature)) {
            problems.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Firma inválida",
                    "Firma HMAC ausente, inválida o fuera de la ventana de tiempo");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("integration", null,
                AuthorityUtils.createAuthorityList(ROLE)));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(new CachedBodyRequest(request, body), response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean validTimestamp(String timestamp) {
        if (timestamp == null || !timestamp.matches("^[0-9]{1,12}$")) {
            return false;
        }
        Instant sent = Instant.ofEpochSecond(Long.parseLong(timestamp));
        Instant now = clock.instant();
        return !sent.isBefore(now.minus(properties.maxClockSkew())) && !sent.isAfter(now.plus(properties.maxClockSkew()));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    /** Lets the controller read the body that the filter already consumed. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            InputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    try {
                        return source.available() == 0;
                    } catch (IOException e) {
                        return true;
                    }
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lectura asíncrona no soportada");
                }

                @Override
                public int read() throws IOException {
                    return source.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
