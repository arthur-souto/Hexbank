package com.hexbank;

import com.hexbank.bootstrap.ApiApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Critério de pronto da Fase 0: sobe, migra, responde e erra conforme o contrato. */
@SpringBootTest(classes = ApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
class PhaseZeroAcceptanceTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void migrationCreatesModuleSchemas() {
        List<String> schemas = jdbc.queryForList(
                "SELECT schema_name FROM information_schema.schemata", String.class);
        assertThat(schemas).contains("accounts", "ledger", "transfers", "funding", "audit");
    }

    @Test
    void healthIsUpWithoutAuthentication() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void protectedRouteWithoutPrincipalReturns401Problem() throws Exception {
        HttpResponse<String> response = get("/v1/accounts");
        assertThat(response.statusCode()).isEqualTo(401);
        assertProblem(response, "UNAUTHENTICATED");
    }

    @Test
    void invalidPrincipalIsTreatedAsAnonymous() throws Exception {
        HttpResponse<String> response = get("/v1/accounts", "X-Dev-Principal", "nao-e-um-uuid");
        assertThat(response.statusCode()).isEqualTo(401);
        assertProblem(response, "UNAUTHENTICATED");
    }

    @Test
    void openApiContractIsPublishedWithDevSecuritySchemes() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"title\":\"HEX BANK API\"")
                .contains("\"name\":\"X-Dev-Principal\"")
                .contains("\"name\":\"X-Dev-Roles\"");
    }

    @Test
    void devRoutesRequireTestAdminRole() throws Exception {
        HttpResponse<String> response = get("/v1/dev/funding", "X-Dev-Principal", UUID.randomUUID().toString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertProblem(response, "FORBIDDEN");
    }

    @Test
    void unknownRouteForAuthenticatedUserReturns404Problem() throws Exception {
        HttpResponse<String> response = get("/v1/rota-que-nao-existe", "X-Dev-Principal", UUID.randomUUID().toString());
        assertThat(response.statusCode()).isEqualTo(404);
        assertProblem(response, "NOT_FOUND");
    }

    @Test
    void echoesClientRequestId() throws Exception {
        HttpResponse<String> response = get("/actuator/health", "X-Request-Id", "req-123");
        assertThat(response.headers().firstValue("X-Request-Id")).hasValue("req-123");
    }

    @Test
    void generatesRequestIdWhenMissingOrInvalid() throws Exception {
        HttpResponse<String> response = get("/actuator/health", "X-Request-Id", "<script>");
        assertThat(response.headers().firstValue("X-Request-Id"))
                .hasValueSatisfying(id -> assertThat(UUID.fromString(id)).isNotNull());
    }

    private HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertProblem(HttpResponse<String> response, String expectedCode) {
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body())
                .contains("\"code\":\"" + expectedCode + "\"")
                .contains("\"requestId\":\"")
                .doesNotContain("Exception");
    }
}