package com.farelo.api.fiscal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** Explicitly provisioned numbering cursor for one issuer/environment/series. */
@Entity
@Table(name = "fiscal_number_sequence")
public class FiscalNumberSequence {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "issuer_cnpj", length = 14, nullable = false, updatable = false)
    private String issuerCnpj;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", length = 12, nullable = false, updatable = false)
    private FiscalNumberEnvironment environment;

    @Column(name = "series", nullable = false, updatable = false)
    private int series;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;

    protected FiscalNumberSequence() { }

    public FiscalNumberSequence(String issuerCnpj, FiscalNumberEnvironment environment,
                                int series, long nextNumber) {
        if (issuerCnpj == null || !issuerCnpj.matches("[0-9]{14}") || environment == null
                || series < 0 || series > 999 || nextNumber < 1 || nextNumber > 999_999_999) {
            throw new IllegalArgumentException("invalid fiscal numbering scope or starting number");
        }
        this.issuerCnpj = issuerCnpj;
        this.environment = environment;
        this.series = series;
        this.nextNumber = nextNumber;
    }

    public UUID getId() { return id; }
    public String getIssuerCnpj() { return issuerCnpj; }
    public FiscalNumberEnvironment getEnvironment() { return environment; }
    public int getSeries() { return series; }
    public long getNextNumber() { return nextNumber; }

    int takeNext() {
        if (nextNumber > 999_999_999) throw new IllegalStateException("fiscal series exhausted");
        return Math.toIntExact(nextNumber++);
    }
}
