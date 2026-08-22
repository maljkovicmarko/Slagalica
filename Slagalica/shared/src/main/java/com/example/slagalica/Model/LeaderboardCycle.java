package com.example.slagalica.Model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class LeaderboardCycle {
    public static final String COLLECTION_NAME = "leaderboard_cycles";
    public static final ZoneId COMPETITION_ZONE = ZoneId.of("Europe/Belgrade");

    public enum Type {
        WEEKLY("weekly"),
        MONTHLY("monthly");

        private final String firestoreValue;

        Type(String firestoreValue) {
            this.firestoreValue = firestoreValue;
        }

        public String getFirestoreValue() {
            return firestoreValue;
        }

        public static Type fromFirestoreValue(String value) {
            for (Type type : values()) {
                if (type.firestoreValue.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown leaderboard cycle type: " + value);
        }
    }

    public enum Status {
        ACTIVE("active"),
        FINALIZING("finalizing"),
        FINALIZED("finalized");

        private final String firestoreValue;

        Status(String firestoreValue) {
            this.firestoreValue = firestoreValue;
        }

        public String getFirestoreValue() {
            return firestoreValue;
        }

        public static Status fromFirestoreValue(String value) {
            for (Status status : values()) {
                if (status.firestoreValue.equalsIgnoreCase(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown leaderboard cycle status: " + value);
        }
    }

    private final String documentId;
    private final String cycleId;
    private final Type type;
    private final Status status;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public LeaderboardCycle(String documentId,
                            String cycleId,
                            Type type,
                            Status status,
                            LocalDate startDate,
                            LocalDate endDate) {
        if (cycleId == null || cycleId.trim().isEmpty()) {
            throw new IllegalArgumentException("cycleId is required");
        }
        if (type == null || status == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("Cycle type, status and dates are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Cycle endDate cannot be before startDate");
        }

        this.documentId = documentId;
        this.cycleId = cycleId;
        this.type = type;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static LeaderboardCycle fromFirestore(String documentId,
                                                  String cycleId,
                                                  String type,
                                                  String status,
                                                  String startDate,
                                                  String endDate) {
        if (type == null || status == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("Cycle type, status, startDate and endDate are required");
        }
        String effectiveCycleId = cycleId == null || cycleId.trim().isEmpty() ? documentId : cycleId.trim();
        try {
            return new LeaderboardCycle(
                    documentId,
                    effectiveCycleId,
                    Type.fromFirestoreValue(type),
                    Status.fromFirestoreValue(status),
                    LocalDate.parse(startDate),
                    LocalDate.parse(endDate)
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid leaderboard cycle fields: " + exception.getMessage(), exception);
        }
    }

    public static LeaderboardCycle currentFallback(Type type) {
        return forDate(type, LocalDate.now(COMPETITION_ZONE));
    }

    public static LeaderboardCycle forDate(Type type, LocalDate date) {
        if (type == null || date == null) {
            throw new IllegalArgumentException("Cycle type and date are required");
        }
        if (type == Type.WEEKLY) {
            LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int weekBasedYear = start.get(WeekFields.ISO.weekBasedYear());
            int weekNumber = start.get(WeekFields.ISO.weekOfWeekBasedYear());
            return new LeaderboardCycle(
                    null,
                    String.format(Locale.ROOT, "weekly_%04d_%02d", weekBasedYear, weekNumber),
                    type,
                    Status.ACTIVE,
                    start,
                    start.plusDays(6)
            );
        }

        LocalDate start = date.withDayOfMonth(1);
        return new LeaderboardCycle(
                null,
                String.format(Locale.ROOT, "monthly_%04d_%02d", date.getYear(), date.getMonthValue()),
                type,
                Status.ACTIVE,
                start,
                date.withDayOfMonth(date.lengthOfMonth())
        );
    }

    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getCycleId() {
        return cycleId;
    }

    public Type getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
