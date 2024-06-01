package searchclient;

import java.util.Comparator;

public abstract class Heuristic
        implements Comparator<State> {
            
            private int[][][][] distances;

            public Heuristic(State initialState) {
                // Here's a chance to pre-process the static parts of the level.
                this.distances = initialState.getDistances();
        
            }

    public int H(State s) { /* improved heuristic */
        int totalDistance = 0;
        int numAgents = s.agentRows.length;
        // For each agent...
        for (int i = 0; i < numAgents; i++) {
            // Find the distance to the nearest goal
            int minDistance = Integer.MAX_VALUE;
            for (int row = 1; row < s.goals.length - 1; row++) {
                for (int col = 1; col < s.goals[row].length - 1; col++) {
                    char goal = s.goals[row][col];
                    if ('0' <= goal && goal <= '9') {
                        int distance = this.distances[s.agentRows[i]][s.agentCols[i]][row][col];
                        minDistance = Math.min(minDistance, distance);
                    }
                }
            }
            totalDistance += minDistance;
        }
        return totalDistance;

    }



    public abstract int f(State s);

    @Override
    public int compare(State s1, State s2) {
        return this.f(s1) - this.f(s2);
    }
}

class HeuristicAStar
        extends Heuristic {
    public HeuristicAStar(State initialState) {
        super(initialState);
    }

    @Override
    public int f(State s) {
        return s.g + this.H(s);
    }

    @Override
    public String toString() {
        return "A* evaluation";
    }
}

class HeuristicWeightedAStar
        extends Heuristic {
    private int w;

    public HeuristicWeightedAStar(State initialState, int w) {
        super(initialState);
        this.w = w;
    }

    @Override
    public int f(State s) {
        return s.g + this.w * this.H(s);
    }

    @Override
    public String toString() {
        return String.format("WA*(%d) evaluation", this.w);
    }
}

class HeuristicGreedy
        extends Heuristic {
    public HeuristicGreedy(State initialState) {
        super(initialState);
    }

    @Override
    public int f(State s) {
        return this.H(s);
    }

    @Override
    public String toString() {
        return "greedy evaluation";
    }
}
