package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.domain.UserRole;
import io.github.diegofranciscog.accounting.security.JwtService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The main use cases through HTTP, as the viewer or Swagger would call them. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiFlowIT extends AbstractIntegrationTest {

    private static final int YEAR = 2049;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    private String admin;
    private String accountant;

    @BeforeAll
    void tokens() {
        admin = "Bearer " + jwt.issue("flujo-admin@demo.ec", UserRole.ADMIN);
        accountant = "Bearer " + jwt.issue("flujo-contador@demo.ec", UserRole.ACCOUNTANT);
    }

    @Test
    @Order(1)
    void adminOpensTheYear() throws Exception {
        mvc.perform(post("/api/v1/fiscal-years/{year}/periods", YEAR).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(12));
        mvc.perform(post("/api/v1/fiscal-years/{year}/periods", YEAR).header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/fiscal-periods").param("year", String.valueOf(YEAR)).header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    @Order(2)
    void accountantDraftsEditsPostsAndReverses() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content(entry("2049-01-10", "50.00", "50.00", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value("flujo-contador@demo.ec"))
                .andReturn();
        String id = id(created);

        mvc.perform(put("/api/v1/journal-entries/{id}", id).header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content(entry("2049-01-11", "60.00", "60.00", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDebit").value(60.00));
        mvc.perform(post("/api/v1/journal-entries/{id}/post", id).header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(YEAR + "-1"));
        mvc.perform(delete("/api/v1/journal-entries/{id}", id).header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/journal-entries/{id}/reverse", id).header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2049-01-20\",\"reason\":\"Prueba\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REVERSAL"));
        mvc.perform(get("/api/v1/journal-entries").param("from", "2049-01-01").param("to", "2049-01-31")
                        .header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        MvcResult draft = mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content(entry("2049-01-12", "1.00", "1.00", false)))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(delete("/api/v1/journal-entries/{id}", id(draft)).header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(3)
    void invalidEntriesGetClearProblems() throws Exception {
        mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content(entry("2049-02-01", "10.00", "9.00", true)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("no cuadra")));
        mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2049-02-01\",\"lines\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.lines").exists());
        mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content(entry("2049-02-01", "10.005", "10.005", true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void reportsAnswerJsonExcelAndPdf() throws Exception {
        mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, accountant)
                .contentType(MediaType.APPLICATION_JSON).content(entry("2049-03-05", "300.00", "300.00", true)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/reports/trial-balance").param("year", String.valueOf(YEAR))
                        .header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true));
        mvc.perform(get("/api/v1/reports/balance-sheet").param("year", String.valueOf(YEAR)).param("month", "3")
                        .header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true));
        mvc.perform(get("/api/v1/reports/income-statement").param("year", String.valueOf(YEAR))
                        .header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(300.00));
        mvc.perform(get("/api/v1/reports/ledger").param("account", "1010101").param("from", "2049-01-01")
                        .param("to", "2049-03-31").header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingBalance").value(300.00));

        mvc.perform(get("/api/v1/reports/journal").param("from", "2049-01-01").param("to", "2049-03-31")
                        .param("format", "XLSX").header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, ReportController.XLSX.toString()))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("libro-diario-2049-01-01_2049-03-31.xlsx")));
        MvcResult pdf = mvc.perform(get("/api/v1/reports/balance-sheet").param("year", String.valueOf(YEAR))
                        .param("month", "3").param("format", "PDF").header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        mvc.perform(get("/api/v1/reports/trial-balance").param("year", "1999").header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.year").exists());
    }

    @Test
    @Order(5)
    void adminImportsChartFromCsv() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "plan-2049.csv", "text/csv",
                "code,name,nature,accepts_entries\n1010198,BANCO FLUJO API,D,true\n".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile exe = new MockMultipartFile("file", "plan.exe", "application/octet-stream", new byte[]{1, 2});

        mvc.perform(multipart("/api/v1/accounts/import").file(csv).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
        mvc.perform(multipart("/api/v1/accounts/import").file(exe).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(multipart("/api/v1/accounts/import").file(csv).header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void masterDataEndpoints() throws Exception {
        mvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"520297\",\"name\":\"GASTO API\",\"acceptsEntries\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentCode").value("5202"));
        mvc.perform(patch("/api/v1/accounts/520297").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/v1/cost-centers").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"VEN-2049\",\"name\":\"Ventas\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/cost-centers").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"VEN-2049\",\"name\":\"Ventas\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/cost-centers/VEN-2049").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ventas Quito\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ventas Quito"));
        mvc.perform(get("/api/v1/cost-centers").header(HttpHeaders.AUTHORIZATION, accountant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("VEN-2049")));
        mvc.perform(get("/api/v1/event-account-mappings").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
        mvc.perform(put("/api/v1/event-account-mappings/INVOICE_ISSUED/VAT_PAYABLE").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountCode\":\"201070101\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void adminManagesUsers() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nuevo-flujo@demo.ec\",\"password\":\"una-clave-larga-2049\","
                                + "\"fullName\":\"Usuario Nuevo\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        mvc.perform(patch("/api/v1/users/{id}/enabled", id(created)).param("value", "false")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"corta@demo.ec\",\"password\":\"corta\",\"fullName\":\"X\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flujo-admin@demo.ec"));
    }

    @Test
    @Order(8)
    void yearEndClosingAndPeriodClose() throws Exception {
        mvc.perform(post("/api/v1/fiscal-years/{year}/closing-entry", YEAR).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CLOSING"));
        mvc.perform(post("/api/v1/fiscal-periods/{year}/{month}/close", YEAR, 1).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/api/v1/journal-entries").header(HttpHeaders.AUTHORIZATION, accountant)
                        .contentType(MediaType.APPLICATION_JSON).content(entry("2049-01-25", "1.00", "1.00", true)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("cerrado")));
    }

    @Test
    @Order(9)
    void openApiIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("contabilidad-api"));
    }

    private static String entry(String date, String debit, String credit, boolean post) {
        return """
                {"date":"%s","description":"Venta flujo API","post":%s,"lines":[
                  {"accountCode":"1010101","debit":%s},
                  {"accountCode":"4101","credit":%s}]}""".formatted(date, post, debit, credit);
    }

    private static String id(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.replaceAll("(?s)^\\{\"id\":(\\d+).*$", "$1");
    }
}
