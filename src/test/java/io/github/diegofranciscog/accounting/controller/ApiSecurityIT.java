package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.domain.UserRole;
import io.github.diegofranciscog.accounting.security.HmacSignatureFilter;
import io.github.diegofranciscog.accounting.security.JwtService;
import io.github.diegofranciscog.accounting.service.FiscalPeriodService;
import io.github.diegofranciscog.accounting.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication, authorisation, HMAC webhooks, rate limiting and security headers (OWASP API Top 10). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiSecurityIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Clave-De-Prueba-" + System.nanoTime();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService users;

    @Autowired
    private JwtService jwt;

    @Autowired
    private FiscalPeriodService periods;

    @BeforeAll
    void createUsers() {
        users.createIfMissing("seguridad-admin@demo.ec", PASSWORD, "Admin de prueba", UserRole.ADMIN);
        users.createIfMissing("seguridad-lector@demo.ec", PASSWORD, "Lector de prueba", UserRole.VIEWER);
        periods.openYear(2048);
    }

    @Test
    void loginReturnsShortLivedBearerToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(ip("10.0.0.1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"SEGURIDAD-ADMIN@demo.ec\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(ip("10.0.0.2")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"seguridad-admin@demo.ec\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
        mvc.perform(post("/api/v1/auth/login").with(ip("10.0.0.3")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@demo.ec\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    void loginIsRateLimited() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/login").with(ip("10.0.0.9")).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"fuerza-bruta@demo.ec\",\"password\":\"x" + attempt + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").with(ip("10.0.0.9")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fuerza-bruta@demo.ec\",\"password\":\"otra\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
    }

    @Test
    void everythingElseRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.valido"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void viewerReadsButCannotWrite() throws Exception {
        String viewer = bearer("seguridad-lector@demo.ec", UserRole.VIEWER);

        mvc.perform(get("/api/v1/accounts/1010101").header(HttpHeaders.AUTHORIZATION, viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptsEntries").value(true));
        mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(entry("2048-01-10", "10.00", "10.00")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, viewer))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedInvoiceEventCreatesEntryOnceAndBadSignaturesAreRejected() throws Exception {
        String body = """
                {"invoiceId":"001-002-000000777","issueDate":"2048-02-15","kind":"GOODS",
                 "subtotal":100.00,"vat":15.00,"total":115.00}""";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = HmacSignatureFilter.sign(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), timestamp,
                body.getBytes(StandardCharsets.UTF_8));

        mvc.perform(signed(body, timestamp, signature)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true));
        mvc.perform(signed(body, timestamp, signature)).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));

        mvc.perform(post("/api/v1/integrations/invoices").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(signed(body.replace("115.00", "116.00"), timestamp, signature)).andExpect(status().isUnauthorized());
        String old = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
        mvc.perform(signed(body, old, HmacSignatureFilter.sign(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), old,
                body.getBytes(StandardCharsets.UTF_8)))).andExpect(status().isUnauthorized());
        // A user token is not enough for webhooks.
        mvc.perform(post("/api/v1/integrations/invoices").header(HttpHeaders.AUTHORIZATION,
                        bearer("seguridad-admin@demo.ec", UserRole.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    void responsesCarrySecurityHeadersAndNoInternals() throws Exception {
        mvc.perform(get("/api/v1/journal-entries/999999999").header(HttpHeaders.AUTHORIZATION,
                        bearer("seguridad-admin@demo.ec", UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void corsOnlyAllowsConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/v1/accounts")
                        .header(HttpHeaders.ORIGIN, "https://atacante.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/accounts")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    private MockHttpServletRequestBuilder signed(String body, String timestamp, String signature) {
        return post("/api/v1/integrations/invoices").contentType(MediaType.APPLICATION_JSON).content(body)
                .header(HmacSignatureFilter.TIMESTAMP_HEADER, timestamp)
                .header(HmacSignatureFilter.SIGNATURE_HEADER, signature);
    }

    private String bearer(String email, UserRole role) {
        return "Bearer " + jwt.issue(email, role);
    }

    private static RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    static String entry(String date, String debit, String credit) {
        return """
                {"date":"%s","description":"Prueba API","post":true,"lines":[
                  {"accountCode":"1010101","debit":%s},
                  {"accountCode":"4101","credit":%s}]}""".formatted(date, debit, credit);
    }
}
