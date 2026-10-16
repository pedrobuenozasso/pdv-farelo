package com.farelo.api.cashier;

import java.util.UUID;

/** Historical sessions cannot be reconciled without a recorded opening float. */
public class CashSessionOpeningAmountUnknownException extends RuntimeException {

    public CashSessionOpeningAmountUnknownException(UUID id) {
        super("Valor de abertura do caixa " + id + " é desconhecido");
    }
}
