package com.farelo.api.payment;

/** Transaction evidence recorded at payment time; never includes card number or CVV. */
public record ElectronicPaymentDetails(String integrationType, String institutionCnpj,
                                       String cardBrand, String authorizationCode,
                                       String receiverCnpj, String terminalId) { }
