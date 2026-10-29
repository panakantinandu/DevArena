package com.devarena.interviews;

public enum InterviewType {
    DSA_CODING("DSA coding"),
    JAVA_BACKEND("Java backend"),
    SPRING_BOOT("Spring Boot"),
    SQL("SQL"),
    DISTRIBUTED_SYSTEMS("Distributed systems"),
    SYSTEM_DESIGN("System design"),
    FULL_STACK("Full stack"),
    BEHAVIORAL("Behavioral"),
    AI_CONCEPTS("AI engineering concepts");

    private final String label;

    InterviewType(String label) { this.label = label; }

    public String label() { return label; }
}
