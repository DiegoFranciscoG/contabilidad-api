package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.Nature;
import io.github.diegofranciscog.accounting.dto.AccountImportResult;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.repository.AccountRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Imports a chart of accounts from CSV: {@code code,name,nature,accepts_entries} (comma or semicolon).
 * The import is all-or-nothing: one invalid row rolls back the whole file. Existing codes are skipped.
 */
@Service
public class AccountImportService {

    public static final long MAX_BYTES = 1_048_576;
    public static final int MAX_ROWS = 5_000;
    private static final Pattern FILE_NAME = Pattern.compile("^[\\w\\- .()]{1,100}\\.csv$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> CONTENT_TYPES = Set.of("text/csv", "text/plain", "application/csv",
            "application/vnd.ms-excel");
    private static final Set<String> HEADERS = Set.of("code", "name", "nature", "accepts_entries");

    private final AccountRepository accounts;
    private final AccountService accountService;

    public AccountImportService(AccountRepository accounts, AccountService accountService) {
        this.accounts = accounts;
        this.accountService = accountService;
    }

    /** Checks name, declared type and size before reading any content. */
    public static void validateFile(String fileName, String contentType, long size) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new DomainException("El archivo debe tener extensión .csv y un nombre simple (letras, números, - _ . ( ))");
        }
        String type = contentType == null ? "" : contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(type)) {
            throw new DomainException("Tipo de archivo no permitido: se espera text/csv");
        }
        if (size <= 0 || size > MAX_BYTES) {
            throw new DomainException("El archivo debe pesar entre 1 byte y 1 MB");
        }
    }

    @Transactional
    public AccountImportResult importCsv(byte[] content) {
        List<Row> rows = parse(decodeUtf8(content));
        rows.sort(Comparator.comparingInt((Row row) -> row.code().length()).thenComparing(Row::code));

        int created = 0;
        List<String> skipped = new ArrayList<>();
        for (Row row : rows) {
            if (accounts.existsByCode(row.code())) {
                skipped.add(row.code());
                continue;
            }
            try {
                Account account = accountService.build(row.code(), row.name(), null, row.nature(), row.acceptsEntries());
                accounts.save(account);
                created++;
            } catch (DomainException | ConflictException e) {
                throw new DomainException("Fila " + row.line() + ": " + e.getMessage());
            }
        }
        return new AccountImportResult(created, skipped.size(), skipped);
    }

    private static String decodeUtf8(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new DomainException("El archivo debe estar codificado en UTF-8");
        }
    }

    private static List<Row> parse(String text) {
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = firstLine.contains(";") ? ';' : ',';
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            List<String> headers = parser.getHeaderNames().stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
            if (!headers.containsAll(List.of("code", "name", "accepts_entries")) || !HEADERS.containsAll(headers)) {
                throw new DomainException("Encabezados esperados: code, name, nature (opcional), accepts_entries");
            }
            List<Row> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) {
                    throw new DomainException("El archivo supera el máximo de " + MAX_ROWS + " filas");
                }
                rows.add(toRow(record));
            }
            if (rows.isEmpty()) {
                throw new DomainException("El archivo no tiene filas");
            }
            return rows;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new DomainException("CSV inválido: revisa comillas y separadores");
        }
    }

    private static Row toRow(CSVRecord record) {
        long line = record.getRecordNumber() + 1;
        String code = value(record, "code");
        String name = value(record, "name");
        if (code.isEmpty() || name.isEmpty()) {
            throw new DomainException("Fila " + line + ": code y name son obligatorios");
        }
        return new Row(line, code, name, nature(value(record, "nature"), line), acceptsEntries(value(record, "accepts_entries"), line));
    }

    private static String value(CSVRecord record, String header) {
        for (String name : record.getParser().getHeaderNames()) {
            if (name.equalsIgnoreCase(header)) {
                return record.isSet(name) ? record.get(name).strip() : "";
            }
        }
        return "";
    }

    private static Nature nature(String value, long line) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "" -> null;
            case "D", "DEBIT", "DEUDORA" -> Nature.DEBIT;
            case "C", "A", "CREDIT", "ACREEDORA" -> Nature.CREDIT;
            default -> throw new DomainException("Fila " + line + ": nature debe ser DEBIT o CREDIT");
        };
    }

    private static boolean acceptsEntries(String value, long line) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "si", "sí", "s", "yes" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw new DomainException("Fila " + line + ": accepts_entries debe ser true o false");
        };
    }

    private record Row(long line, String code, String name, Nature nature, boolean acceptsEntries) {
    }
}
