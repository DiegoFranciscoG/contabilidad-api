package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.domain.AccountElement;
import io.github.diegofranciscog.accounting.domain.Nature;
import io.github.diegofranciscog.accounting.dto.AccountImportResult;
import io.github.diegofranciscog.accounting.dto.AccountResponse;
import io.github.diegofranciscog.accounting.dto.CreateAccountRequest;
import io.github.diegofranciscog.accounting.dto.UpdateAccountRequest;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceIT extends AbstractIntegrationTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private AccountImportService importer;

    @Test
    void createInfersParentFromLongestPrefix() {
        AccountResponse created = accounts.create(new CreateAccountRequest("1010199", "BANCO DEMO PRUEBAS", null, null, true));

        assertThat(created.parentCode()).isEqualTo("10101");
        assertThat(created.element()).isEqualTo(AccountElement.ASSET);
        assertThat(created.nature()).isEqualTo(Nature.DEBIT);
        assertThatThrownBy(() -> accounts.create(new CreateAccountRequest("1010199", "OTRA", null, null, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateRenamesAndDeactivates() {
        accounts.create(new CreateAccountRequest("520299", "GASTO TEMPORAL", "5202", null, true));

        AccountResponse updated = accounts.update("520299", new UpdateAccountRequest("GASTO RENOMBRADO", false));

        assertThat(updated.name()).isEqualTo("GASTO RENOMBRADO");
        assertThat(updated.active()).isFalse();
        assertThatThrownBy(() -> accounts.get("999")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotHangAccountsFromDetailAccount() {
        assertThatThrownBy(() -> accounts.create(new CreateAccountRequest("101010101", "SUBCAJA", null, null, true)))
                .isInstanceOf(DomainException.class).hasMessageContaining("acepta movimientos");
    }

    @Test
    void csvImportCreatesHierarchyInAnyOrder() {
        String csv = """
                code;name;nature;accepts_entries
                10101049901;CUENTA CORRIENTE BANCO DEMO;D;true
                101010499;BANCOS DEL EXTERIOR;;false
                1010104;BANCOS EXTRANJEROS;DEBIT;no
                1010101;CAJA GENERAL;D;true
                """;

        AccountImportResult result = importer.importCsv(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.created()).isEqualTo(3);
        assertThat(result.skippedCodes()).containsExactly("1010101");
        assertThat(accounts.get("10101049901").parentCode()).isEqualTo("101010499");
    }

    @Test
    void csvImportIsAllOrNothing() {
        String csv = """
                code,name,nature,accepts_entries
                1010105,BANCOS NUEVOS,D,false
                1010105999,MAL COLGADA,D,maybe
                """;

        assertThatThrownBy(() -> importer.importCsv(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DomainException.class).hasMessageContaining("Fila 3");
        assertThatThrownBy(() -> accounts.get("1010105")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void csvImportRejectsBadContent() {
        assertThatThrownBy(() -> importer.importCsv("code,name\n1,ACTIVO".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DomainException.class).hasMessageContaining("Encabezados");
        assertThatThrownBy(() -> importer.importCsv(new byte[]{(byte) 0xC3, (byte) 0x28}))
                .isInstanceOf(DomainException.class).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> importer.importCsv("code,name,accepts_entries\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DomainException.class).hasMessageContaining("no tiene filas");
    }

    @Test
    void uploadedFileIsValidatedBeforeReading() {
        AccountImportService.validateFile("plan.csv", "text/csv", 100);

        assertThatThrownBy(() -> AccountImportService.validateFile("plan.exe", "text/csv", 100))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AccountImportService.validateFile("../plan.csv", "text/csv", 100))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AccountImportService.validateFile("plan.csv", "application/pdf", 100))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AccountImportService.validateFile("plan.csv", "text/csv", AccountImportService.MAX_BYTES + 1))
                .isInstanceOf(DomainException.class);
    }
}
