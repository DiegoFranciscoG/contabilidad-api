package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.regex.Pattern;

@Entity
@Table(name = "cost_centers")
public class CostCenter {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9-]{1,20}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    protected CostCenter() {
    }

    public CostCenter(String code, String name) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new DomainException("Código de centro de costo inválido (A-Z, 0-9 y guion; máximo 20)");
        }
        this.code = code;
        rename(name);
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank() || newName.length() > 120) {
            throw new DomainException("El nombre del centro de costo es obligatorio (máximo 120 caracteres)");
        }
        this.name = newName.strip();
    }

    public void changeActive(boolean newActive) {
        this.active = newActive;
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

    public boolean isActive() {
        return active;
    }
}
