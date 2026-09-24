package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Chart of accounts node (plan de cuentas). Codes are hierarchical: a child starts with its parent's code (R4). */
@Entity
@Table(name = "accounts")
public class Account {

    private static final Pattern CODE = Pattern.compile("^[1-5][0-9]{0,19}$");
    private static final int MAX_LEVEL = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private Account parent;

    @Column(nullable = false, updatable = false)
    private short level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AccountElement element;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Nature nature;

    @Column(name = "accepts_entries", nullable = false)
    private boolean acceptsEntries;

    @Column(nullable = false)
    private boolean active = true;

    protected Account() {
    }

    private Account(String code, String name, Account parent, Nature nature, boolean acceptsEntries) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new DomainException("Código de cuenta inválido: solo dígitos, empieza en 1-5 y máximo 20 caracteres");
        }
        this.code = code;
        this.element = AccountElement.fromCode(code);
        this.parent = parent;
        if (parent == null) {
            if (code.length() != 1) {
                throw new DomainException("Una cuenta sin padre debe tener código de un dígito: " + code);
            }
            this.level = 1;
        } else {
            if (!code.startsWith(parent.code) || code.length() <= parent.code.length()) {
                throw new DomainException("El código " + code + " debe empezar con el código de su cuenta padre " + parent.code);
            }
            if (parent.acceptsEntries) {
                throw new DomainException("La cuenta padre " + parent.code + " acepta movimientos y no puede tener subcuentas");
            }
            if (parent.level >= MAX_LEVEL) {
                throw new DomainException("Se superó el nivel máximo de " + MAX_LEVEL);
            }
            this.level = (short) (parent.level + 1);
        }
        rename(name);
        this.nature = nature == null ? element.normalNature() : nature;
        this.acceptsEntries = acceptsEntries;
    }

    /** Creates an account; when {@code nature} is null the element's normal nature is used. */
    public static Account create(String code, String name, Account parent, Nature nature, boolean acceptsEntries) {
        return new Account(code, name, parent, nature, acceptsEntries);
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank() || newName.length() > 200) {
            throw new DomainException("El nombre de la cuenta es obligatorio (máximo 200 caracteres)");
        }
        this.name = newName.strip();
    }

    public void changeActive(boolean newActive) {
        this.active = newActive;
    }

    /** Signed balance from this account's nature: positive = normal balance. */
    public BigDecimal balance(BigDecimal debit, BigDecimal credit) {
        return EntryRules.balance(nature, debit, credit);
    }

    public boolean isContraAccount() {
        return nature != element.normalNature();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Account getParent() {
        return parent;
    }

    public int getLevel() {
        return level;
    }

    public AccountElement getElement() {
        return element;
    }

    public Nature getNature() {
        return nature;
    }

    public boolean isAcceptsEntries() {
        return acceptsEntries;
    }

    public boolean isActive() {
        return active;
    }
}
