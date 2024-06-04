package searchclient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class State {
    public int[] agentRows;
    public int[] agentCols;
    public static Color[] agentColors;
    public static boolean[][] walls;
    public char[][] boxes;
    public static char[][] goals;
    public static Color[] boxColors;
    public State parent;
    public int g;
    private int hash = 0;
    public int currentAgentIndex;
    private Action leadingAction; // the action that lead to this state being generated
    private int[][][][] distances; // Distances between all cells in the grid

    // Constructs an initial state.
    public State(int[] agentRows, int[] agentCols, Color[] agentColors, boolean[][] walls,
            char[][] boxes, Color[] boxColors, char[][] goals) {
        this.agentRows = agentRows;
        this.agentCols = agentCols;
        this.agentColors = agentColors;
        this.walls = walls;
        this.boxes = boxes;
        this.boxColors = boxColors;
        this.goals = goals;
        this.parent = null;
        this.g = 0;
    }

    // Constructs the state resulting from applying jointAction in parent.
    public State(State parent, Action currentAgentAction, int currentAgentIndex) {
        // Copy parent
        this.agentRows = Arrays.copyOf(parent.agentRows, parent.agentRows.length);
        this.agentCols = Arrays.copyOf(parent.agentCols, parent.agentCols.length);
        this.boxes = new char[parent.boxes.length][];
        for (int i = 0; i < parent.boxes.length; i++) {
            this.boxes[i] = Arrays.copyOf(parent.boxes[i], parent.boxes[i].length);
        }
        // Set own parameters
        this.parent = parent;
        this.g = parent.g + 1;
        this.currentAgentIndex = currentAgentIndex;
        this.leadingAction = currentAgentAction; // Store the action that lead to this state being generated
        // Apply the action for the current agent
        char box;
        switch (currentAgentAction.type) {
            case NoOp:
                break;
            case Move:
                this.agentRows[currentAgentIndex] += currentAgentAction.agentRowDelta;
                this.agentCols[currentAgentIndex] += currentAgentAction.agentColDelta;
                break;
            case Push:
                this.agentRows[currentAgentIndex] += currentAgentAction.agentRowDelta;
                this.agentCols[currentAgentIndex] += currentAgentAction.agentColDelta;
                box = this.boxes[this.agentRows[currentAgentIndex]][this.agentCols[currentAgentIndex]];
                this.boxes[this.agentRows[currentAgentIndex]
                        + currentAgentAction.boxRowDelta][this.agentCols[currentAgentIndex]
                                + currentAgentAction.boxColDelta] = box;
                this.boxes[this.agentRows[currentAgentIndex]][this.agentCols[currentAgentIndex]] = 0;
                break;
            case Pull:
                box = this.boxes[this.agentRows[currentAgentIndex] -
                        currentAgentAction.boxRowDelta][this.agentCols[currentAgentIndex]
                                - currentAgentAction.boxColDelta];
                this.boxes[this.agentRows[currentAgentIndex]][this.agentCols[currentAgentIndex]] = box;
                this.boxes[this.agentRows[currentAgentIndex]
                        - currentAgentAction.boxRowDelta][this.agentCols[currentAgentIndex]
                                - currentAgentAction.boxColDelta] = 0;
                this.agentRows[currentAgentIndex] += currentAgentAction.agentRowDelta;
                this.agentCols[currentAgentIndex] += currentAgentAction.agentColDelta;
                break;
        }
    }

    public boolean isGoalStateForAgent(int agentIndex) {
        // Check if the agent is at the goal cell
        char agentChar = (char) ('0' + agentIndex);

        // Check if the agent has reached its goal position
        if (State.goals[this.agentRows[agentIndex]][this.agentCols[agentIndex]] != agentChar) {
            return false;
        }
        // Check if the agent boxes are goal placed
        for (int row = 1; row < this.goals.length - 1; row++) {
            for (int col = 1; col < this.goals[row].length - 1; col++) {
                char goal = this.goals[row][col];

                if ('A' <= goal && goal <= 'Z' && this.boxes[row][col] != goal
                        && this.boxColors[goal - 'A'] == this.agentColors[agentIndex]) {
                    return false;
                }
            }
        }
        return true;
    }

    public Action resolveConflicts(Action[][] previousPlans, Action currentAgentAction) {
        for (int i = previousPlans.length - 1; i >= 0; i--) {
            // Check if the previous action is null or if this.g is out of bounds
            if (previousPlans[i] == null || this.g >= previousPlans[i].length) {
                // If it's null or out of bounds, skip this iteration and go to the next one
                continue;
            }
            // Check if there is a conflict between the current action and the previous
            // action
            if (conflicts(currentAgentAction, previousPlans[i][this.g], this.currentAgentIndex, i)) {
                // If there is a conflict, concede to the previous agent's plan by making this
                // action a NoOp
                return Action.NoOp;
            }
        }
        // If no conflict was found with any of the previous plans, the action is valid
        return currentAgentAction;

    }

    int[] calculatePositions(Action action, int agentRow, int agentCol) {
        int agentDestinationRow = -1, agentDestinationCol = -1, boxRow = -1, boxCol = -1;
        switch (action.type) {
            case NoOp:
                agentDestinationRow = agentRow;
                agentDestinationCol = agentCol;
                boxRow = -1;
                boxCol = -1;
                break;
            case Move:
                agentDestinationRow = agentRow + action.agentRowDelta;
                agentDestinationCol = agentCol + action.agentColDelta;
                boxRow = -1;
                boxCol = -1;
                break;
            case Push:
                agentDestinationRow = agentRow + action.agentRowDelta;
                agentDestinationCol = agentCol + action.agentColDelta;
                boxRow = agentDestinationRow + action.boxRowDelta;
                boxCol = agentDestinationCol + action.boxColDelta;
                break;
            case Pull:
                agentDestinationRow = agentRow + action.agentRowDelta;
                agentDestinationCol = agentCol + action.agentColDelta;
                boxRow = agentRow - action.boxRowDelta;
                boxCol = agentCol - action.boxColDelta;
                break;
        }
        return new int[] { agentDestinationRow, agentDestinationCol, boxRow, boxCol };
    }

    private boolean conflicts(Action action1, Action action2, int agentIndex1, int agentIndex2) { // Helper function to
                                                                                                  // check if two
                                                                                                  // actions conflict
        int agent1Row = this.agentRows[agentIndex1];
        int agent1Col = this.agentCols[agentIndex1];
        int agent2Row = this.agentRows[agentIndex2];
        int agent2Col = this.agentCols[agentIndex2];

        int[] positions1 = calculatePositions(action1, agent1Row, agent1Col);
        int[] positions2 = calculatePositions(action2, agent2Row, agent2Col);

        // Check for conflicts
        if (action1.type == ActionType.NoOp || action2.type == ActionType.NoOp) {
            return false; // No conflict if either action is NoOp
        }
        if (action1.type == ActionType.Move && action2.type == ActionType.Move && positions1[0] == positions2[0]
                && positions1[1] == positions2[1]) {
            return true; // Both agents attempt to occupy the same cell
        }
        if (action1.type == ActionType.Pull && action2.type == ActionType.Pull && positions1[2] == positions2[2]
                && positions1[3] == positions2[3]) {
            return true; // Both agents attempt to pull the same box
        }
        if (action1.type == ActionType.Push && action2.type == ActionType.Push && positions1[0] == positions2[0]
                && positions1[1] == positions2[1]) {
            return true; // Both agents attempt to push the same box
        }
        if (action1.type == ActionType.Pull && action2.type == ActionType.Push
                && (positions1[2] == positions2[0] && positions1[3] == positions2[1])) {
            return true; // First agent tries to pull a box that the other tries to push
        }
        if (action1.type == ActionType.Push && action2.type == ActionType.Pull
                && (positions1[0] == positions2[2] && positions1[1] == positions2[3])) {
            return true; // First agent tries to push a box that the other tries to pull
        }
        if (action1.type == ActionType.Move && action2.type == ActionType.Push
                && (positions1[0] == positions2[2] && positions1[1] == positions2[3])) {
            return true; // An agent tries to move where a box is being pushed in
        }

        return false;
    }

    public ArrayList<State> getExpandedStatesSequential(Action[][] previousPlans, int currentAgentIndex) {
        ArrayList<State> expandedStates = new ArrayList<>(16);
        Action currentAgentAction = Action.NoOp;

        // Generate child states for the current agent ONLY
        for (Action action : Action.values()) {
            if (this.isApplicable(currentAgentIndex, action)) {
                currentAgentAction = action;
                State childState = new State(this, currentAgentAction, currentAgentIndex);
                expandedStates.add(childState);
            }
        }

        return expandedStates;
    }

    private boolean isApplicable(int agent, Action action) {
        int agentRow = this.agentRows[agent];
        int agentCol = this.agentCols[agent];
        Color agentColor = State.agentColors[agent];
        int boxRow;
        int boxCol;
        Color boxColor;
        char box;
        int destinationRow;
        int destinationCol;
        switch (action.type) {
            case NoOp:
                return true;

            case Move:
                destinationRow = agentRow + action.agentRowDelta;
                destinationCol = agentCol + action.agentColDelta;
                return this.cellIsFree(destinationRow, destinationCol);
            case Push:
                destinationRow = agentRow + action.agentRowDelta;
                destinationCol = agentCol + action.agentColDelta;
                box = this.boxes[destinationRow][destinationCol];
                if (box == 0) {
                    return false;
                }
                boxColor = State.boxColors[box - 'A'];
                if (agentColor != boxColor) {
                    return false;
                }
                boxRow = destinationRow + action.boxRowDelta;
                boxCol = destinationCol + action.boxColDelta;
                return this.cellIsFree(boxRow, boxCol);
            case Pull:
                destinationRow = agentRow + action.agentRowDelta;
                destinationCol = agentCol + action.agentColDelta;
                boxRow = agentRow - action.boxRowDelta;
                boxCol = agentCol - action.boxColDelta;
                box = this.boxes[boxRow][boxCol];
                if (box == 0) {
                    return false;
                }
                boxColor = State.boxColors[box - 'A'];
                if (agentColor != boxColor) {
                    return false;
                }
                return this.cellIsFree(destinationRow, destinationCol);
        }

        // Unreachable:
        return false;
    }

    private boolean cellIsFree(int row, int col) {
        return !State.walls[row][col] && this.boxes[row][col] == 0 && this.agentAt(row, col) == 0;
    }

    private char agentAt(int row, int col) {
        for (int i = 0; i < this.agentRows.length; i++) {
            if (this.agentRows[i] == row && this.agentCols[i] == col) {
                return (char) ('0' + i);
            }
        }
        return 0;
    }

    public Action[] extractPlanForCurrentAgent() {
        Action[] plan = new Action[this.g];
        State state = this; // Start from the current state (which is the goal state)
        int step = this.g - 1; // The step counter is equal to the cost of the plan

        // Climb down the tree of states, appending leading actions
        for (int i = step; i >= 0; i--) {
            plan[i] = state.leadingAction; // Append the leading action
            state = state.parent; // Move to the parent state
        }
        return plan;
    }

    @Override
    public int hashCode() {
        if (this.hash == 0) {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(this.agentColors);
            result = prime * result + Arrays.hashCode(this.boxColors);
            result = prime * result + Arrays.deepHashCode(this.walls);
            result = prime * result + Arrays.deepHashCode(this.goals);
            result = prime * result + Arrays.hashCode(this.agentRows);
            result = prime * result + Arrays.hashCode(this.agentCols);
            for (int row = 0; row < this.boxes.length; ++row) {
                for (int col = 0; col < this.boxes[row].length; ++col) {
                    char c = this.boxes[row][col];
                    if (c != 0) {
                        result = prime * result + (row * this.boxes[row].length + col) * c;
                    }
                }
            }
            this.hash = result;
        }
        return this.hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        State other = (State) obj;
        return Arrays.equals(this.agentRows, other.agentRows) &&
                Arrays.equals(this.agentCols, other.agentCols) &&
                Arrays.equals(this.agentColors, other.agentColors) &&
                Arrays.deepEquals(this.walls, other.walls) &&
                Arrays.deepEquals(this.boxes, other.boxes) &&
                Arrays.equals(this.boxColors, other.boxColors) &&
                Arrays.deepEquals(this.goals, other.goals);
    }

    public int[][][][] getDistances() {
        if (this.distances == null) {
            this.computeDistances();
        }
        return this.distances;
    }

    private void computeDistances() {
        int rows = walls.length;
        int cols = walls[0].length;
        distances = new int[rows][cols][rows][cols];

        for (int startRow = 0; startRow < rows; startRow++) {
            for (int startCol = 0; startCol < cols; startCol++) {
                // Initialize all distances to a large number
                for (int endRow = 0; endRow < rows; endRow++) {
                    for (int endCol = 0; endCol < cols; endCol++) {
                        distances[startRow][startCol][endRow][endCol] = Integer.MAX_VALUE;
                    }
                }

                // Use BFS to compute the shortest distances from (startRow, startCol) to all
                // other cells

                bfs(startRow, startCol);
            }
        }
    }

    private void bfs(int startRow, int startCol) {
        int rows = walls.length;
        int cols = walls[0].length;

        boolean[][] visited = new boolean[rows][cols];
        visited[startRow][startCol] = true;

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[] { startRow, startCol, 0 }); // The third element of the array is the distance from the start
        // cell

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int row = cell[0];
            int col = cell[1];
            int distance = cell[2];

            // Update the distance from the start cell to this cell
            distances[startRow][startCol][row][col] = distance;

            for (int[] dir : new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];

                if (newRow >= 0 && newRow < rows && newCol >= 0 && newCol < cols &&
                        !walls[newRow][newCol] && !visited[newRow][newCol]) {
                    visited[newRow][newCol] = true;
                    queue.add(new int[] { newRow, newCol, distance + 1 });
                }
            }
        }
    }
}