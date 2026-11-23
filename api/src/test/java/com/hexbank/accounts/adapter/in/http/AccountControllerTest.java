package com.hexbank.accounts.adapter.in.http;

import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.accounts.adapter.in.http.handler.AccountExceptionHandler;
import com.hexbank.accounts.application.port.in.GetAccountQuery;
import com.hexbank.accounts.application.port.in.OpenAccountUseCase;
import com.hexbank.accounts.application.port.in.command.OpenAccountCommand;
import com.hexbank.accounts.application.port.in.view.AccountBalanceView;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.accounts.domain.AccountStatus;
import com.hexbank.accounts.domain.exceptions.AccountNotFoundException;
import com.hexbank.identity.config.SecurityConfig;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.adapter.in.http.ApiExceptionHandler;
import com.hexbank.shared.adapter.in.http.RequestIdFilter;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Especificação: docs/architecture/ARCHITECTURE.md, "ACC-UC-01 a 03" (cenários HTTP).
 * Slice web: portas mockadas; segurança, filtros e advices reais (problem+json com code e requestId).
 * A identidade vem do filtro dev (X-Dev-Principal), ativo no profile "test".
 */
@WebMvcTest(AccountController.class)
@ContextConfiguration(classes = ApiApplication.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, ApiExceptionHandler.class, AccountExceptionHandler.class, RequestIdFilter.class})
class AccountControllerTest {

    private static final String PRINCIPAL_HEADER = "X-Dev-Principal";
    private static final UUID OWNER_UUID = UUID.fromString("0d9c8b7a-6f5e-4d3c-8b2a-1f0e9d8c7b6a");
    private static final PrincipalId OWNER = new PrincipalId(OWNER_UUID);
    private static final UUID ACCOUNT_UUID = UUID.fromString("5b2e8f1a-3c4d-4e6f-9a8b-7c6d5e4f3a2b");
    private static final AccountId ACCOUNT_ID = new AccountId(ACCOUNT_UUID);
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Autowired MockMvc mockMvc;

    @MockitoBean OpenAccountUseCase openAccountUseCase;
    @MockitoBean GetAccountQuery getAccountQuery;

    private static Account activeAccount() {
        return Account.open(ACCOUNT_ID, OWNER, InstanceCurrency.BRL, NOW);
    }

    // ---------- ACC-UC-01 ----------

    @Test
    void openAccount_returns201WithBodyAndLocation() throws Exception {
        when(openAccountUseCase.open(any())).thenReturn(activeAccount());

        mockMvc.perform(post("/v1/accounts")
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"BRL\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/accounts/" + ACCOUNT_UUID))
                .andExpect(jsonPath("$.id").value(ACCOUNT_UUID.toString()))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    @Test
    void openAccount_passesPrincipalAndRequestIdToCommand() throws Exception {
        when(openAccountUseCase.open(any())).thenReturn(activeAccount());

        mockMvc.perform(post("/v1/accounts")
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString())
                        .header(RequestIdFilter.HEADER, "req-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"BRL\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
        verify(openAccountUseCase).open(captor.capture());
        assertThat(captor.getValue().ownerId()).isEqualTo(OWNER);
        assertThat(captor.getValue().currency()).isEqualTo(InstanceCurrency.BRL);
        assertThat(captor.getValue().requestId()).isEqualTo("req-123");
    }

    @Test
    void openAccount_ignoresOwnerIdFromBody() throws Exception {
        UUID other = UUID.fromString("9f8e7d6c-5b4a-4c3d-8e2f-1a0b9c8d7e6f");
        when(openAccountUseCase.open(any())).thenReturn(activeAccount());

        mockMvc.perform(post("/v1/accounts")
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"BRL\",\"ownerId\":\"" + other + "\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
        verify(openAccountUseCase).open(captor.capture());
        assertThat(captor.getValue().ownerId()).isEqualTo(OWNER);
    }

    @Test
    void openAccount_withUnsupportedCurrency_returns400() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(openAccountUseCase);
    }

    @Test
    void openAccount_withMissingCurrency_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("currency"));

        verifyNoInteractions(openAccountUseCase);
    }

    @Test
    void openAccount_withoutPrincipal_returns401() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"BRL\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(openAccountUseCase);
    }

    // ---------- ACC-UC-02 ----------

    @Test
    void getAccount_returns200() throws Exception {
        when(getAccountQuery.get(OWNER, ACCOUNT_ID)).thenReturn(activeAccount());

        mockMvc.perform(get("/v1/accounts/{id}", ACCOUNT_UUID)
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_UUID.toString()))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getAccount_notFoundOrNotOwner_returns404WithFixedDetail() throws Exception {
        when(getAccountQuery.get(OWNER, ACCOUNT_ID))
                .thenThrow(new AccountNotFoundException("Account not found: " + ACCOUNT_UUID));

        mockMvc.perform(get("/v1/accounts/{id}", ACCOUNT_UUID)
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
    }

    @Test
    void getAccount_blockedAccount_returns200() throws Exception {
        Account blocked = Account.restore(ACCOUNT_ID, OWNER, InstanceCurrency.BRL, AccountStatus.BLOCKED, NOW, null);
        when(getAccountQuery.get(OWNER, ACCOUNT_ID)).thenReturn(blocked);

        mockMvc.perform(get("/v1/accounts/{id}", ACCOUNT_UUID)
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void getAccount_withNonUuidId_returns400() throws Exception {
        mockMvc.perform(get("/v1/accounts/abc")
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(getAccountQuery);
    }

    @Test
    void getAccount_withoutPrincipal_returns401() throws Exception {
        mockMvc.perform(get("/v1/accounts/{id}", ACCOUNT_UUID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------- ACC-UC-03 ----------

    @Test
    void getBalance_returnsMinorUnitsAndCurrency() throws Exception {
        var view = new AccountBalanceView(ACCOUNT_UUID,
                new Money(InstanceCurrency.BRL, 10_000),
                new Money(InstanceCurrency.BRL, 2_500),
                new Money(InstanceCurrency.BRL, 7_500),
                NOW);
        when(getAccountQuery.balance(OWNER, ACCOUNT_ID)).thenReturn(view);

        mockMvc.perform(get("/v1/accounts/{id}/balance", ACCOUNT_UUID)
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_UUID.toString()))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.bookedMinorUnits").value(10_000))
                .andExpect(jsonPath("$.reservedMinorUnits").value(2_500))
                .andExpect(jsonPath("$.availableMinorUnits").value(7_500))
                .andExpect(jsonPath("$.asOf").value("2026-11-16T12:00:00Z"));
    }

    @Test
    void getBalance_notFoundOrNotOwner_returns404() throws Exception {
        when(getAccountQuery.balance(OWNER, ACCOUNT_ID))
                .thenThrow(new AccountNotFoundException("Account not found"));

        mockMvc.perform(get("/v1/accounts/{id}/balance", ACCOUNT_UUID)
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void getBalance_withNonUuidId_returns400() throws Exception {
        mockMvc.perform(get("/v1/accounts/abc/balance")
                        .header(PRINCIPAL_HEADER, OWNER_UUID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
