package com.hexbank.accounts.adapter.in.http;

import com.hexbank.accounts.adapter.in.http.request.OpenAccountRequest;
import com.hexbank.accounts.adapter.in.http.response.AccountBalanceResponse;
import com.hexbank.accounts.adapter.in.http.response.AccountResponse;
import com.hexbank.identity.domain.PrincipalId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Contrato HTTP + documentação OpenAPI de contas.
 * O {@link AccountController} só implementa; mapeamentos e binding ficam lá.
 */
@Tag(name = "Contas", description = "Abertura e consulta de contas. O dono é sempre o principal autenticado (BR-004).")
public interface AccountApi {

    String PROBLEM_JSON = "application/problem+json";
    String PROBLEM_400_EXAMPLE = """
            {"status":400,"title":"Bad Request","detail":"Requisição inválida","instance":"/v1/accounts",
             "code":"INVALID_REQUEST","requestId":"9c1f...","errors":[{"field":"currency","message":"must not be null"}]}""";

    @Operation(summary = "Abrir conta",
            description = "Abre uma conta ACTIVE em BRL para o principal autenticado. O dono nunca vem do corpo "
                    + "(um `ownerId` enviado é ignorado). Cria também a conta contábil (LIABILITY) e a auditoria "
                    + "`ACCOUNT_CREATED` na mesma transação. Um dono pode ter várias contas.")
    @ApiResponse(responseCode = "201", description = "Conta criada. Header `Location` aponta para a conta.")
    @ApiResponse(responseCode = "400", description = "`INVALID_REQUEST`: moeda ausente, não suportada (ex.: USD) ou corpo ilegível. "
            + "Em moeda ausente, `errors` lista os campos.", content = @Content(mediaType = PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetail.class),
            examples = @ExampleObject(value = PROBLEM_400_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "`UNAUTHENTICATED`: sem principal.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<AccountResponse> open(OpenAccountRequest request,
                                         @Parameter(hidden = true) PrincipalId principalId,
                                         @Parameter(hidden = true) HttpServletRequest servletRequest);

    @Operation(summary = "Consultar conta",
            description = "Retorna id, moeda e status. Funciona em qualquer status (ACTIVE, BLOCKED, CLOSED). "
                    + "Conta de outro dono responde 404, igual a inexistente, para não revelar que ela existe.")
    @ApiResponse(responseCode = "200", description = "Conta encontrada.")
    @ApiResponse(responseCode = "400", description = "`INVALID_REQUEST`: id não é um UUID.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "`UNAUTHENTICATED`: sem principal.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "`ACCOUNT_NOT_FOUND`: inexistente ou de outro dono.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "Id da conta (UUID)") UUID id,
            @Parameter(hidden = true) PrincipalId principalId);

    @Operation(summary = "Consultar saldo",
            description = "Saldo contábil (`booked`), reservado e disponível em minor units (centavos), "
                    + "vindos do ledger. `available = booked - reserved`.")
    @ApiResponse(responseCode = "200", description = "Saldo da conta.")
    @ApiResponse(responseCode = "400", description = "`INVALID_REQUEST`: id não é um UUID.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "`UNAUTHENTICATED`: sem principal.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "`ACCOUNT_NOT_FOUND`: inexistente ou de outro dono.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<AccountBalanceResponse> getBalance(
            @Parameter(description = "Id da conta (UUID)") UUID id,
            @Parameter(hidden = true) PrincipalId principalId);
}
