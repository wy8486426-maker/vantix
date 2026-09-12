package com.sinognss.cloud.vantix.common;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ServiceCodeGenerator {
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int RANDOM_CHARACTERS = 20;

    private final SecureRandom random = new SecureRandom();

    public String generate(LocalDate date) {
        return "VX" + DATE_FORMAT.format(date) + randomSuffix(RANDOM_CHARACTERS);
    }

    public String generateBatchNo(LocalDate date) {
        return "GB" + DATE_FORMAT.format(date) + randomSuffix(RANDOM_CHARACTERS);
    }

    private String randomSuffix(int length) {
        StringBuilder suffix = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return suffix.toString();
    }
}