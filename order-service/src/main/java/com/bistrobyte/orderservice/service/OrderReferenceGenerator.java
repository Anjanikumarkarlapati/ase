package com.bistrobyte.orderservice.service;

import com.bistrobyte.orderservice.repository.CustomerOrderRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Produces short, human-readable tracking codes such as {@code BB-20260921-7F3A}. */
@Component
public class OrderReferenceGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int SUFFIX_LENGTH = 4;
    private static final int MAX_ATTEMPTS = 25;

    private final SecureRandom random = new SecureRandom();
    private final CustomerOrderRepository repository;

    public OrderReferenceGenerator(CustomerOrderRepository repository) {
        this.repository = repository;
    }

    public String next() {
        String datePart = LocalDate.now(ZoneId.systemDefault()).format(DATE_PART);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = "BB-" + datePart + "-" + randomSuffix();
            if (!repository.existsByOrderReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique order reference after "
                + MAX_ATTEMPTS + " attempts");
    }

    private String randomSuffix() {
        StringBuilder builder = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
