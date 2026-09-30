package com.travelprice.domain;

import jakarta.persistence.*;

@Entity
public class PriceMention {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Evidence evidence;
    @Column(nullable = false)
    private String item;
    private String unit;
    @Column(nullable = false)
    private long amount;
    protected PriceMention() {}
    public PriceMention(Evidence evidence, String item, String unit, long amount) {
        this.evidence=evidence; this.item=item; this.unit=unit; this.amount=amount;
    }
    public Evidence getEvidence() { return evidence; }
    public String getItem() { return item; }
    public String getUnit() { return unit; }
    public long getAmount() { return amount; }
}
