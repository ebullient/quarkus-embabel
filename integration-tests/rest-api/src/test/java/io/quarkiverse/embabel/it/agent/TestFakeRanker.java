package io.quarkiverse.embabel.it.agent;

import java.util.Collection;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import com.embabel.agent.api.common.ranking.Ranker;
import com.embabel.agent.api.common.ranking.Ranking;
import com.embabel.agent.api.common.ranking.Rankings;
import com.embabel.common.core.types.Described;
import com.embabel.common.core.types.Named;

@Alternative
@Priority(1)
@ApplicationScoped
public class TestFakeRanker implements Ranker {

    private static String favoredMatch;
    private static double defaultScore = 0.9;

    public static void favor(String nameOrDescriptionSubstring) {
        favoredMatch = nameOrDescriptionSubstring;
    }

    public static void scoreAll(double score) {
        favoredMatch = null;
        defaultScore = score;
    }

    public static void reset() {
        favoredMatch = null;
        defaultScore = 0.9;
    }

    @Override
    public <T extends Named & Described> Rankings<T> rank(
            String description,
            String userInput,
            Collection<? extends T> rankables) {
        List<Ranking<T>> rankings = rankables.stream()
                .map(item -> new Ranking<>(item, scoreFor(item)))
                .toList();
        return new Rankings<>(rankings);
    }

    private <T extends Named & Described> double scoreFor(T item) {
        if (favoredMatch == null) {
            return defaultScore;
        }
        if (item.getName().contains(favoredMatch)
                || item.getDescription().contains(favoredMatch)) {
            return 0.9;
        }
        return 0.1;
    }
}
