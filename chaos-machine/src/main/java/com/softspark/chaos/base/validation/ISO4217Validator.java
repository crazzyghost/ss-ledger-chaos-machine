package com.softspark.chaos.base.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for ISO 4217 currency codes.
 */
public class ISO4217Validator implements ConstraintValidator<ISO4217, String> {

    private static final Set<String> VALID_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return VALID_CODES.contains(value.toUpperCase());
    }
}
