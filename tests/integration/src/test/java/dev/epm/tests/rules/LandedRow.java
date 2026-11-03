package dev.epm.tests.rules;

import java.math.BigDecimal;

/** Landed-row fact for the validation rule set (test-scoped fixture fact). */
public class LandedRow {

    private String rowId;
    private String accountId;
    private BigDecimal amount;
    private boolean amountIsNull;
    private String currency;
    private String category;
    private String naturalKey;
    private boolean quarantined;
    private String violationCode;

    public LandedRow(
            String rowId,
            String accountId,
            String amountText,
            String currency,
            String category,
            String naturalKey) {
        this.rowId = rowId;
        this.accountId = accountId;
        this.amountIsNull = amountText == null || amountText.isBlank();
        this.amount = amountIsNull ? null : new BigDecimal(amountText);
        this.currency = currency;
        this.category = category;
        this.naturalKey = naturalKey;
    }

    public String getRowId() {
        return rowId;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isAmountIsNull() {
        return amountIsNull;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCategory() {
        return category;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public boolean isQuarantined() {
        return quarantined;
    }

    public void setQuarantined(boolean quarantined) {
        this.quarantined = quarantined;
    }

    public String getViolationCode() {
        return violationCode;
    }

    public void setViolationCode(String violationCode) {
        this.violationCode = violationCode;
    }
}
