package com.farelo.api.ordering;

public class OrderOptionInvalidException extends RuntimeException {
    public OrderOptionInvalidException(String message) {
        super(message);
    }
}
