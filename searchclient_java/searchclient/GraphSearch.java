package searchclient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class GraphSearch {

    public static Action[][] search(State initialState, Frontier frontier) {
        // Graph-Search algorithm from R&N figure 3.7
        int iterations = 0;
        frontier.add(initialState);
        HashSet<State> expanded = new HashSet<>();

        while (true) {
            // Print a status message every 10000 iteration
            if (++iterations % 10000 == 0) {
                printSearchStatus(expanded, frontier);
            }
            if (frontier.isEmpty()) {
                return null;
            }

            State state = frontier.pop();
            expanded.add(state);

            if (state.isGoalState()) {
                printSearchStatus(expanded, frontier);
                return state.extractPlan();
            }

            for (State child : state.getExpandedStates()) {
                if (!frontier.contains(child) && !expanded.contains(child)) {
                    frontier.add(child);
                }
            }
        }

    }

    private static long startTime = System.nanoTime();

    private static void printSearchStatus(HashSet<State> expanded, Frontier frontier) {
        String statusTemplate = "#Expanded: %,8d, #Frontier: %,8d, #Generated: %,8d, Time: %3.3f s\n%s\n";
        double elapsedTime = (System.nanoTime() - startTime) / 1_000_000_000d;
        System.err.format(statusTemplate, expanded.size(), frontier.size(), expanded.size() + frontier.size(),
                elapsedTime, Memory.stringRep());
    }
}
