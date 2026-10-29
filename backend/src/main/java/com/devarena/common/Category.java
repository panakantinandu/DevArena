package com.devarena.common;

/** Skill areas shown on the dashboard. Every topic belongs to exactly one category. */
public enum Category {
    DSA("Data structures & algorithms"),
    JAVA("Java"),
    SPRING("Spring Boot"),
    DATABASES("Databases"),
    DISTRIBUTED("Distributed systems"),
    SYSTEM_DESIGN("System design"),
    FRONTEND("Frontend"),
    BEHAVIORAL("Behavioral"),
    AI_CONCEPTS("AI engineering concepts");

    private final String label;

    Category(String label) { this.label = label; }

    public String label() { return label; }
}
