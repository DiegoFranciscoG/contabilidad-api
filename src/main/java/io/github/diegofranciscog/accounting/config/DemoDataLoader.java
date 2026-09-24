package io.github.diegofranciscog.accounting.config;

import io.github.diegofranciscog.accounting.domain.EntryType;
import io.github.diegofranciscog.accounting.domain.UserRole;
import io.github.diegofranciscog.accounting.dto.CreateCostCenterRequest;
import io.github.diegofranciscog.accounting.dto.InvoiceIssuedEvent;
import io.github.diegofranciscog.accounting.dto.JournalEntryRequest;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.JournalLineRequest;
import io.github.diegofranciscog.accounting.dto.PayrollAccruedEvent;
import io.github.diegofranciscog.accounting.dto.ReverseEntryRequest;
import io.github.diegofranciscog.accounting.dto.RevenueKind;
import io.github.diegofranciscog.accounting.repository.FiscalPeriodRepository;
import io.github.diegofranciscog.accounting.service.CostCenterService;
import io.github.diegofranciscog.accounting.service.FiscalPeriodService;
import io.github.diegofranciscog.accounting.service.JournalEntryService;
import io.github.diegofranciscog.accounting.service.UserService;
import io.github.diegofranciscog.accounting.service.integration.IntegrationEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Fictitious company "Comercial Andina Demo" for the public demo (DEMO_DATA=true). Every entry goes through
 * the same services, rules and triggers as real data. Runs once: it skips when the current year already exists.
 */
@Component
@Order(2)
public class DemoDataLoader implements ApplicationRunner {

    public static final String DEMO_VIEWER = "demo@demo.com";
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String DEMO_ACCOUNTANT = "contador.demo@demo.com";

    private final BootstrapProperties properties;
    private final UserService users;
    private final FiscalPeriodService periods;
    private final FiscalPeriodRepository periodRepository;
    private final CostCenterService costCenters;
    private final JournalEntryService entries;
    private final IntegrationEventService integrations;
    private final Clock clock;
    /** Nothing after this date is recorded, so the demo never shows future entries. */
    private LocalDate cutoff;

    public DemoDataLoader(BootstrapProperties properties, UserService users, FiscalPeriodService periods,
                          FiscalPeriodRepository periodRepository, CostCenterService costCenters,
                          JournalEntryService entries, IntegrationEventService integrations, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.periods = periods;
        this.periodRepository = periodRepository;
        this.costCenters = costCenters;
        this.entries = entries;
        this.integrations = integrations;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.demoData()) {
            return;
        }
        if (!properties.hasDemoPassword()) {
            throw new IllegalStateException("DEMO_DATA=true requiere DEMO_PASSWORD (mínimo 12 caracteres)");
        }
        users.createIfMissing(DEMO_VIEWER, properties.demoPassword(), "Usuario demo (solo lectura)", UserRole.VIEWER);

        LocalDate today = LocalDate.now(clock);
        int year = today.getYear();
        if (periodRepository.existsByYear((short) year)) {
            return;
        }
        cutoff = today;
        periods.openYear(year);
        costCenters.create(new CreateCostCenterRequest("ADM", "Administración"));
        costCenters.create(new CreateCostCenterRequest("VEN", "Ventas"));
        loadYear(year, today);
        log.info("Datos demo cargados para {}", year);
    }

    private void loadYear(int year, LocalDate today) {
        int currentMonth = today.getMonthValue();
        post(LocalDate.of(year, 1, 2), "Asiento de apertura del ejercicio", EntryType.OPENING,
                debit("1010103", "42000.00"), debit("1010306", "18500.00"), debit("1020105", "6200.00"),
                debit("1020108", "9800.00"), credit("1020112", "3100.00"), credit("2010301", "7400.00"),
                credit("2020301", "15000.00"), credit("30101", "40000.00"), credit("30401", "2500.00"),
                credit("30601", "8500.00"));

        for (int month = 1; month <= currentMonth; month++) {
            YearMonth ym = YearMonth.of(year, month);
            BigDecimal seasonal = BigDecimal.valueOf(100 + (month * 7) % 23).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal goods = amount("14800.00", seasonal);
            BigDecimal services = amount("3200.00", seasonal);

            invoice(ym, 1, 8, RevenueKind.GOODS, goods);
            invoice(ym, 2, 21, RevenueKind.SERVICES, services);
            post(ym.atDay(9), "Costo de mercadería vendida", EntryType.STANDARD,
                    debit("5101", amount("8900.00", seasonal).toPlainString()),
                    credit("1010306", amount("8900.00", seasonal).toPlainString()));
            post(ym.atDay(12), "Compra de mercadería a proveedor local", EntryType.STANDARD,
                    debit("1010306", "9000.00"), debit("1010501", "1350.00"), credit("2010301", "10350.00"));
            post(ym.atDay(18), "Cobro a clientes", EntryType.STANDARD,
                    debit("1010103", amount("16500.00", seasonal).toPlainString()),
                    credit("101020502", amount("16500.00", seasonal).toPlainString()));
            post(ym.atDay(20), "Pago a proveedores", EntryType.STANDARD,
                    debit("2010301", "9800.00"), credit("1010103", "9800.00"));
            post(ym.atDay(5), "Arriendo de oficina y bodega", EntryType.STANDARD,
                    debitCenter("520209", "1400.00", "ADM"), credit("1010103", "1400.00"));
            post(ym.atDay(25), "Servicios básicos y telecomunicaciones", EntryType.STANDARD,
                    debitCenter("520218", "380.00", "ADM"), credit("1010103", "380.00"));
            post(ym.atDay(26), "Publicidad digital", EntryType.STANDARD,
                    debitCenter("520111", "450.00", "VEN"), credit("1010103", "450.00"));
            if (!ym.atEndOfMonth().isAfter(cutoff)) {
                integrations.payrollAccrued(new PayrollAccruedEvent("NOM-" + ym, ym.atEndOfMonth(),
                        new BigDecimal("4820.00"), new BigDecimal("455.49"), new BigDecimal("537.43"),
                        new BigDecimal("803.33"), "ADM"));
                post(ym.atEndOfMonth(), "Pago de nómina y aportes IESS del mes", EntryType.STANDARD,
                        debit("201070401", "4364.51"), debit("2010703", "992.92"), credit("1010103", "5357.43"));
                post(ym.atEndOfMonth(), "Depreciación mensual de propiedades, planta y equipo", EntryType.STANDARD,
                        debitCenter("52022101", "265.00", "ADM"), credit("1020112", "265.00"));
            }
            post(ym.atDay(15), "Intereses del préstamo bancario", EntryType.STANDARD,
                    debit("520301", "137.50"), credit("1010103", "137.50"));
        }

        if (currentMonth > 3) {
            JournalEntryResponse duplicated = post(LocalDate.of(year, 2, 14), "Pago duplicado de servicios básicos",
                    EntryType.STANDARD, debitCenter("520218", "380.00", "ADM"), credit("1010103", "380.00"));
            entries.reverse(duplicated.id(), new ReverseEntryRequest(LocalDate.of(year, 3, 3),
                    "Pago duplicado detectado en conciliación bancaria"), DEMO_ACCOUNTANT);
        }

        for (int month = 1; month < currentMonth; month++) {
            periods.close(year, month, DEMO_ACCOUNTANT);
        }
    }

    private void invoice(YearMonth ym, int sequence, int day, RevenueKind kind, BigDecimal subtotal) {
        if (ym.atDay(day).isAfter(cutoff)) {
            return;
        }
        BigDecimal vat = subtotal.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
        String id = "FAC-" + ym + "-" + sequence;
        integrations.invoiceIssued(new InvoiceIssuedEvent(id, ym.atDay(day), kind, subtotal, vat, subtotal.add(vat),
                kind == RevenueKind.GOODS ? "VEN" : "ADM"));
    }

    private JournalEntryResponse post(LocalDate date, String description, EntryType type, JournalLineRequest... lines) {
        if (date.isAfter(cutoff)) {
            return null;
        }
        return entries.create(new JournalEntryRequest(date, description, type, new ArrayList<>(List.of(lines)), true),
                DEMO_ACCOUNTANT);
    }

    private static BigDecimal amount(String base, BigDecimal factor) {
        return new BigDecimal(base).multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private static JournalLineRequest debit(String account, String amount) {
        return new JournalLineRequest(account, null, new BigDecimal(amount), null, null);
    }

    private static JournalLineRequest debitCenter(String account, String amount, String costCenter) {
        return new JournalLineRequest(account, costCenter, new BigDecimal(amount), null, null);
    }

    private static JournalLineRequest credit(String account, String amount) {
        return new JournalLineRequest(account, null, null, new BigDecimal(amount), null);
    }
}
