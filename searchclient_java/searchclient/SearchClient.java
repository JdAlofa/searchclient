package searchclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

public class SearchClient {
    public static State parseLevel(BufferedReader serverMessages)
            throws IOException {
        // We can assume that the level file is conforming to specification, since the
        // server verifies this.
        // Read domain
        serverMessages.readLine(); // #domain
        serverMessages.readLine(); // hospital

        // Read Level name
        serverMessages.readLine(); // #levelname
        serverMessages.readLine(); // <name>

        // Read colors
        serverMessages.readLine(); // #colors
        Color[] agentColors = new Color[10];
        Color[] boxColors = new Color[26];
        String line = serverMessages.readLine();
        while (!line.startsWith("#")) {
            String[] split = line.split(":");
            Color color = Color.fromString(split[0].strip());
            String[] entities = split[1].split(",");
            for (String entity : entities) {
                char c = entity.strip().charAt(0);
                if ('0' <= c && c <= '9') {
                    agentColors[c - '0'] = color;
                } else if ('A' <= c && c <= 'Z') {
                    boxColors[c - 'A'] = color;
                }
            }
            line = serverMessages.readLine();
        }
        // Read initial state
        // line is currently "#initial"
        int numRows = 0;
        int numCols = 0;
        ArrayList<String> levelLines = new ArrayList<>(64);
        line = serverMessages.readLine();
        while (!line.startsWith("#")) {
            levelLines.add(line);
            numCols = Math.max(numCols, line.length());
            ++numRows;
            line = serverMessages.readLine();
        }
        int numAgents = 0;
        int[] agentRows = new int[10];
        int[] agentCols = new int[10];
        boolean[][] walls = new boolean[numRows][numCols];
        char[][] boxes = new char[numRows][numCols];
        for (int row = 0; row < numRows; ++row) {
            line = levelLines.get(row);
            for (int col = 0; col < line.length(); ++col) {
                char c = line.charAt(col);

                if ('0' <= c && c <= '9') {
                    agentRows[c - '0'] = row;
                    agentCols[c - '0'] = col;
                    ++numAgents;
                } else if ('A' <= c && c <= 'Z') {
                    boxes[row][col] = c;
                } else if (c == '+') {
                    walls[row][col] = true;
                }
            }
        }
        agentRows = Arrays.copyOf(agentRows, numAgents);
        agentCols = Arrays.copyOf(agentCols, numAgents);

        // Read goal state
        // line is currently "#goal"
        char[][] goals = new char[numRows][numCols];
        line = serverMessages.readLine();
        int row = 0;
        while (!line.startsWith("#")) {
            for (int col = 0; col < line.length(); ++col) {
                char c = line.charAt(col);

                if (('0' <= c && c <= '9') || ('A' <= c && c <= 'Z')) {
                    goals[row][col] = c;
                }
            }
            ++row;
            line = serverMessages.readLine();
        }
        return new State(agentRows, agentCols, agentColors, walls, boxes, boxColors, goals);
    }

    public static void main(String[] args)
            throws IOException {
        // Send client name to server.
        System.out.println("SearchClient");
        // Parse the level.
        BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
        State initialState = SearchClient.parseLevel(serverMessages);
        // Select search strategy.
        Frontier frontier;
        if (args.length > 0) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "-bfs":
                    frontier = new FrontierBFS();
                    break;
                case "-dfs":
                    frontier = new FrontierDFS();
                    break;
                case "-astar":
                    frontier = new FrontierBestFirst(new HeuristicAStar(initialState));
                    break;
                case "-wastar":
                    int w = 5;
                    if (args.length > 1) {
                        try {
                            w = Integer.parseUnsignedInt(args[1]);
                        } catch (NumberFormatException e) {
                            System.err.println("Couldn't parse weight argument to -wastar as integer, using default.");
                        }
                    }
                    frontier = new FrontierBestFirst(new HeuristicWeightedAStar(initialState, w));
                    break;
                case "-greedy":
                    frontier = new FrontierBestFirst(new HeuristicGreedy(initialState));
                    break;
                default:
                    frontier = new FrontierBestFirst(new HeuristicAStar(initialState));
                    System.err.println("Defaulting to Astar search. Use arguments -bfs, -dfs, -astar, -wastar, or " +
                            "-greedy to set the search strategy.");
            }
        } else {
            frontier = new FrontierBestFirst(new HeuristicAStar(initialState));
        }

        // Search for a plan.
        Action[][] plan;
        try {
            plan = SearchClient.search(initialState, frontier, 0);
        } catch (OutOfMemoryError ex) {
            System.err.println("Maximum memory usage exceeded.");
            plan = null;
        }

        // Print plan to server.
        if (plan == null) {
            System.err.println("Unable to solve level.");
            System.exit(0);
        } else {
            System.err.format("Found solution of length %,d.\n", plan.length);

            for (Action[] jointAction : plan) {
                System.out.print(jointAction[0].name + "@" + jointAction[0].name);
                for (int action = 1; action < jointAction.length; ++action) {
                    System.out.print("|");
                    System.out.print(jointAction[action].name);
                }
                System.out.println();
                // We must read the server's response to not fill up the stdin buffer and block
                // the server.
                serverMessages.readLine();
            }
        }
    }

    public static Action[][] search(State initialState, Frontier frontier, int agentIndex) {
        // System.err.format("Starting %s.\n", frontier.getName());
        int iterations = 0;
        frontier.add(initialState);
        HashSet<State> expanded = new HashSet<>();
        Action[][] previousPlans = new Action[initialState.agentRows.length][];
        State resetState = initialState;

        while (true) {
            State state = frontier.pop();
            expanded.add(state);

            // Print a status message every 10000 iteration
            if (++iterations % 10000 == 0) {
                printSearchStatus(expanded, frontier);
            }
            
            if (agentIndex == initialState.agentRows.length) {
                System.err.println("All individual plans found.");
                for (int i = 0; i < previousPlans.length; i++) {
                    System.err.print("Plan " + i + " : " + Arrays.toString(previousPlans[i]));
                    System.err.println();
                }
                // Find the length of the longest plan
                int maxPlanLength = 0;
                for (Action[] plan : previousPlans) {
                    if (plan != null && plan.length > maxPlanLength) {
                        maxPlanLength = plan.length;
                    }
                }
                Action[][] combinedPlan = new Action[maxPlanLength][state.agentRows.length];
                for (int i = 0; i < maxPlanLength; i++) {
                    for (int j = 0; j < state.agentRows.length; j++) {
                        if (previousPlans[j] != null && i < previousPlans[j].length
                                && previousPlans[j][i] != null) {
                            combinedPlan[i][j] = previousPlans[j][i];
                        } else {
                            combinedPlan[i][j] = Action.NoOp;
                        }
                    }
                }

                System.err.println("Combined plans: ");
                for (Action[] actions : combinedPlan) {
                    for (Action action : actions) {
                        System.err.print(action + " ");
                    }
                    System.err.println();
                }
                return combinedPlan; // Return the combined plan
            }
            // Check if goal state for the current agent
            if (state.isGoalStateForAgent(agentIndex)) { // Check for individual goal state
                System.err.println("Goal state reached for agent " + agentIndex);
                printSearchStatus(expanded, frontier);
                // Fill previousPlans with the plan for the current agent
                previousPlans[agentIndex] = state.extractPlanForCurrentAgent(); // Extract plan after reaching goal

                // Resolve conflicts
                ArrayList<Action> conflictedPlan = new ArrayList<>(Arrays.asList(previousPlans[agentIndex]));
                if (initialState.agentRows.length > 1 && previousPlans[0] != null) {

                    for (int i = 0; i < conflictedPlan.size(); i++) {
                        Action conflictedAction = state.resolveConflicts(previousPlans, conflictedPlan.get(i));
                        if (conflictedAction.equals(conflictedPlan.get(i))) {
                            continue;
                        } else {
                            conflictedPlan.add(i, conflictedAction);
                        }
                    }

                    previousPlans[agentIndex] = conflictedPlan.toArray(new Action[conflictedPlan.size()]);
                }

                System.err.println("Plan for agent " + agentIndex + " : " + Arrays.toString(previousPlans[agentIndex]));

                if (initialState.agentRows.length == 1) {
                    Action[][] combinedPlan = new Action[state.g][state.agentRows.length];
                    for (int i = 0; i < state.g; i++) {
                        for (int j = 0; j < state.agentRows.length; j++) {
                            if (previousPlans[j] != null && i < previousPlans[j].length) {
                                combinedPlan[i][j] = previousPlans[j][i];
                            } else {
                                combinedPlan[i][j] = Action.NoOp;
                            }

                        }
                    }
                    return combinedPlan; // Return the combined plan

                } else {
                    if (agentIndex < initialState.agentRows.length) {
                        // Deep copy the initial state for the next agent
                        resetState = new State(initialState.agentRows.clone(), initialState.agentCols.clone(),
                                initialState.agentColors, initialState.walls,
                                initialState.boxes, initialState.boxColors, initialState.goals);
                        for (int i = 0; i < previousPlans[agentIndex].length; i++) {
                            resetState = new State(resetState, previousPlans[agentIndex][i], agentIndex);
                        }
                        resetState.g = 0;
                        resetState.currentAgentIndex = agentIndex + 1;
                        resetState.parent = null;
                        frontier = new FrontierBestFirst(new HeuristicAStar(resetState));
                        expanded.clear();
                        frontier.add(resetState);
                        agentIndex++; // Increment agent index
                        continue;
                    } else {
                        // all agent have reached their goals
                        continue;
                    }
                }
            }

            // Expand the state for the current agent
            for (State child : state.getExpandedStatesSequential(previousPlans, agentIndex)) {
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