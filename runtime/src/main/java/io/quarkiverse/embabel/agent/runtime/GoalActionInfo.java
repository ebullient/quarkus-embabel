package io.quarkiverse.embabel.agent.runtime;

import java.util.Objects;

/**
 * Build-time metadata about an @AchievesGoal action.
 * <p>
 * Captured at build time by Jandex scanning and passed to runtime for goal creation.
 * This avoids runtime reflection and ensures consistent goal naming with Spring Boot.
 */
public final class GoalActionInfo {
    private final String fullyQualifiedActionName;
    private final String description;

    public GoalActionInfo(String fullyQualifiedActionName, String description) {
        this.fullyQualifiedActionName = Objects.requireNonNull(fullyQualifiedActionName);
        this.description = Objects.requireNonNull(description);
    }

    public String getFullyQualifiedActionName() {
        return fullyQualifiedActionName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GoalActionInfo that = (GoalActionInfo) o;
        return fullyQualifiedActionName.equals(that.fullyQualifiedActionName) &&
                description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullyQualifiedActionName, description);
    }

    @Override
    public String toString() {
        return "GoalActionInfo{" +
                "action='" + fullyQualifiedActionName + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
