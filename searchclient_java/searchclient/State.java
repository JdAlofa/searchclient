package searchclient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class State {
    private static final Random RNG = new Random(1);

    /*
     * The agent rows, columns, and colors are indexed by the agent number.
     * For example, this.agentRows[0] is the row location of agent '0'.
     */
    public int[] agentRows;
    public int[] agentCols;
    public static Color[] agentColors;

    /*
     * The walls, boxes, and goals arrays are indexed from the top-left of the
     * level, row-major order (row, col).
     * Col 0 Col 1 Col 2 Col 3
     * Row 0: (0,0) (0,1) (0,2) (0,3) ...
     * Row 1: (1,0) (1,1) (1,2) (1,3) ...
     * Row 2: (2,0) (2,1) (2,2) (2,3) ...
     * ...
     *
     * For example, this.walls[2] is an array of booleans for the third row.
     * this.walls[row][col] is true if there's a wall at (row, col).
     *
     * this.boxes and this.char are two-dimensional arrays of chars.
     * this.boxes[1][2]='A' means there is an A box at (1,2).
     * If there is no box at (1,2), we have this.boxes[1][2]=0 (null character).
     * Simiarly for goals.
     *
     */
    public static boolean[][] walls;
    public char[][] boxes;
    public static char[][] goals;

    /*
     * The box colors are indexed alphabetically. So this.boxColors[0] is the color
     * of A boxes,
     * this.boxColor[1] is the color of B boxes, etc.
     */
    public static Color[] boxColors;

    public final State parent;
    public Action[] jointAction;
    public final int g;

    private int hash = 0;

    // Constructs an initial state.
    // Arguments are not copied, and therefore should not be modified after being
    // passed in.
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
        this.jointAction = null;
        this.g = 0;
        // **Change:** Initialize jointAction
        this.jointAction = new Action[agentRows.length]; // Initialize with the number of agents
        // for (int i = 0; i < agentRows.length; i++) {
        //     this.jointAction[i] = Action.NoOp;
        // }

    }

    // Constructs the state resulting from applying jointAction in parent.
    // Precondition: Joint action must be applicable and non-conflicting in parent
    // state.
    private State(State parent, Action[] jointAction) {
        // Copy parent
        this.agentRows = Arrays.copyOf(parent.agentRows, parent.agentRows.length);
        this.agentCols = Arrays.copyOf(parent.agentCols, parent.agentCols.length);
        this.boxes = new char[parent.boxes.length][];
        for (int i = 0; i < parent.boxes.length; i++) {
            this.boxes[i] = Arrays.copyOf(parent.boxes[i], parent.boxes[i].length);
        }

        // Set own parameters
        this.parent = parent;
        this.jointAction = Arrays.copyOf(jointAction, jointAction.length);
        this.g = parent.g + 1;

        // Apply each action
        int numAgents = this.agentRows.length;
        for (int agent = 0; agent < numAgents; ++agent) {
            Action action = jointAction[agent];
            char box;

            switch (action.type) {
                case NoOp:
                    break;

                case Move:
                    this.agentRows[agent] += action.agentRowDelta;
                    this.agentCols[agent] += action.agentColDelta;
                    break;

                case Push:
                    this.agentRows[agent] += action.agentRowDelta;
                    this.agentCols[agent] += action.agentColDelta;
                    box = this.boxes[this.agentRows[agent]][this.agentCols[agent]];
                    this.boxes[this.agentRows[agent] + action.boxRowDelta][this.agentCols[agent]
                            + action.boxColDelta] = box;
                    this.boxes[this.agentRows[agent]][this.agentCols[agent]] = 0;
                    break;

                case Pull:
                    box = this.boxes[this.agentRows[agent] -
                            action.boxRowDelta][this.agentCols[agent]
                                    - action.boxColDelta];
                    this.boxes[this.agentRows[agent]][this.agentCols[agent]] = box;
                    this.boxes[this.agentRows[agent] - action.boxRowDelta][this.agentCols[agent]
                            - action.boxColDelta] = 0;
                    this.agentRows[agent] += action.agentRowDelta;
                    this.agentCols[agent] += action.agentColDelta;
                    break;
            }
        }
    }

    public int g() {
        return this.g;
    }

    public boolean isGoalState() {
        for (int row = 1; row < this.goals.length - 1; row++) {
            for (int col = 1; col < this.goals[row].length - 1; col++) {
                char goal = this.goals[row][col];

                if ('A' <= goal && goal <= 'Z' && this.boxes[row][col] != goal) {
                    return false;
                } else if ('0' <= goal && goal <= '9' &&
                        !(this.agentRows[goal - '0'] == row && this.agentCols[goal - '0'] == col)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Function to resolve conflicts with previously generated plans
    private void resolveConflicts(Action[][] previousPlans) {
        // Assuming the agent plans are for agents 0, 1, 2, ...
        int numAgents = this.agentRows.length;
        for (int agent = 1; agent < numAgents; ++agent) {
            for (int i = 0; i < previousPlans[agent - 1].length; ++i) {
                if (previousPlans[agent - 1][i] == Action.NoOp) {
                    continue;
                }

                // Check for conflicts with the plan for agent (agent-1)
                for (int j = 0; j < this.jointAction.length; ++j) {
                    if (this.jointAction[j] == Action.NoOp) {
                        continue;
                    }

                    if (conflicts(this.jointAction[j], previousPlans[agent - 1][i])) {
                        // Concede to the previous agent's plan
                        this.jointAction[j] = Action.NoOp;
                        break; // Move to the next action in the previous plan
                    }
                }
            }
        }
    }

    // Helper function to check if two actions conflict
    private boolean conflicts(Action action1, Action action2) {
        // Two actions conflict if they both attempt to occupy the same cell
        // or if they both attempt to move the same box.
        int agent1Row = this.agentRows[0] + action1.agentRowDelta;
        int agent1Col = this.agentCols[0] + action1.agentColDelta;

        int agent2Row = this.agentRows[0] + action2.agentRowDelta;
        int agent2Col = this.agentCols[0] + action2.agentColDelta;

        // Check if actions involve moving the same box
        if (action1.boxRowDelta != 0 && action2.boxRowDelta != 0 &&
                (action1.boxRowDelta == action2.boxRowDelta && action1.boxColDelta == action2.boxColDelta)) {
            return true;
        }

        return agent1Row == agent2Row && agent1Col == agent2Col;
    }

    public ArrayList<State> getExpandedStates() {
        int numAgents = this.agentRows.length;

        // Determine list of applicable actions for each individual agent.
        Action[][] applicableActions = new Action[numAgents][];
        for (int agent = 0; agent < numAgents; ++agent) {
            ArrayList<Action> agentActions = new ArrayList<>(Action.values().length);
            for (Action action : Action.values()) {
                if (this.isApplicable(agent, action)) {
                    agentActions.add(action);
                }
            }
            applicableActions[agent] = agentActions.toArray(new Action[0]);
        }

        // Iterate over joint actions, check conflict and generate child states.
        Action[] jointAction = new Action[numAgents];
        int[] actionsPermutation = new int[numAgents];
        ArrayList<State> expandedStates = new ArrayList<>(16);
        while (true) {
            for (int agent = 0; agent < numAgents; ++agent) {
                jointAction[agent] = applicableActions[agent][actionsPermutation[agent]];
            }

            if (!this.isConflicting(jointAction)) {
                expandedStates.add(new State(this, jointAction));
            }

            // Advance permutation
            boolean done = false;
            for (int agent = 0; agent < numAgents; ++agent) {
                if (actionsPermutation[agent] < applicableActions[agent].length - 1) {
                    ++actionsPermutation[agent];
                    break;
                } else {
                    actionsPermutation[agent] = 0;
                    if (agent == numAgents - 1) {
                        done = true;
                    }
                }
            }

            // Last permutation?
            if (done) {
                break;
            }
        }

        Collections.shuffle(expandedStates, State.RNG);
        return expandedStates;
    }

    // Function to resolve conflicts with previously generated plans
    public ArrayList<State> getExpandedStatesSequential(Action[][] previousPlans) {
        int numAgents = this.agentRows.length;
        ArrayList<State> expandedStates = new ArrayList<>(16);

        for (int agent = 0; agent < numAgents; ++agent) {
            // Create a new state where all other agents are static
            State agentState = this.createStaticStateForAgent(agent);
            // Generate actions for the current agent
            Action[] agentActions = agentState.generateAgentActions();
            // Generate child states for the current agent
            for (Action action : agentActions) {
                Action[] jointAction = new Action[numAgents];
                Arrays.fill(jointAction, Action.NoOp); // Initialize all actions to NoOp
                jointAction[agent] = action;
                // **Change:** Create a new State object and resolve conflicts
                State childState = new State(agentState, jointAction);
                childState.resolveConflicts(previousPlans); // Resolve conflicts with previous plans
                expandedStates.add(childState);
            }
        }

        return expandedStates;
    }

    private Action[] generateAgentActions() {
        ArrayList<Action> agentActions = new ArrayList<>(Action.values().length);
        for (Action action : Action.values()) {
            if (this.isApplicable(0, action)) {
                agentActions.add(action);
            }
        }
        return agentActions.toArray(new Action[0]);
    }

    private State createStaticStateForAgent(int agentIndex) {
        int[] agentRowsCopy = Arrays.copyOf(this.agentRows, this.agentRows.length);
        int[] agentColsCopy = Arrays.copyOf(this.agentCols, this.agentCols.length);
        char[][] boxesCopy = new char[this.boxes.length][];
        for (int i = 0; i < this.boxes.length; i++) {
            boxesCopy[i] = Arrays.copyOf(this.boxes[i], this.boxes[i].length);
        }

        State agentState = new State(agentRowsCopy, agentColsCopy, agentColors, walls,
                boxesCopy, boxColors, goals);

        // Set all other agents to NoOp
        for (int i = 0; i < agentState.agentRows.length; i++) {
            if (i != agentIndex) {
                agentState.jointAction[i] = Action.NoOp;
            }
        }

        return agentState;
    }

    private boolean isApplicable(int agent, Action action) {
        int agentRow = this.agentRows[agent];
        int agentCol = this.agentCols[agent];
        Color agentColor = this.agentColors[agent];
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
                boxColor = this.boxColors[box - 'A'];
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
                boxColor = this.boxColors[box - 'A'];
                if (agentColor != boxColor) {
                    return false;
                }
                return this.cellIsFree(destinationRow, destinationCol);
        }

        // Unreachable:
        return false;
    }

    private boolean isConflicting(Action[] jointAction) {
        int numAgents = this.agentRows.length;

        int[] destinationRows = new int[numAgents]; // row of new cell to become occupied by action
        int[] destinationCols = new int[numAgents]; // column of new cell to become occupied by action
        int[] boxRows = new int[numAgents]; // current row of box moved by action
        int[] boxCols = new int[numAgents]; // current column of box moved by action

        // Collect cells to be occupied and boxes to be moved
        for (int agent = 0; agent < numAgents; ++agent) {
            Action action = jointAction[agent];
            int agentRow = this.agentRows[agent];
            int agentCol = this.agentCols[agent];
            int boxRow;
            int boxCol;

            switch (action.type) {
                case NoOp:
                    break;

                case Move:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    boxRows[agent] = agentRow; // Distinct dummy value
                    boxCols[agent] = agentCol; // Distinct dummy value
                    break;
                case Push:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    boxRow = destinationRows[agent] + action.boxRowDelta;
                    boxCol = destinationCols[agent] + action.boxColDelta;
                    boxRows[agent] = boxRow;
                    boxCols[agent] = boxCol;
                    break;
                case Pull:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    boxRow = agentRow - action.boxRowDelta;
                    boxCol = agentCol - action.boxColDelta;
                    boxRows[agent] = boxRow;
                    boxCols[agent] = boxCol;
                    break;
            }
        }

        for (int a1 = 0; a1 < numAgents; ++a1) {
            if (jointAction[a1] == Action.NoOp) {
                continue;
            }

            for (int a2 = a1 + 1; a2 < numAgents; ++a2) {
                if (jointAction[a2] == Action.NoOp) {
                    continue;
                }

                // Moving into same cell?
                if (destinationRows[a1] == destinationRows[a2] && destinationCols[a1] == destinationCols[a2]) {
                    return true;
                }

                // Moving the same box?
                if (boxRows[a1] == boxRows[a2] && boxCols[a1] == boxCols[a2]) {
                    return true;
                }

                // Agent moving into cell where box is being pushed?
                if (destinationRows[a1] == boxRows[a2] && destinationCols[a1] == boxCols[a2]) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean cellIsFree(int row, int col) {
        return !this.walls[row][col] && this.boxes[row][col] == 0 && this.agentAt(row, col) == 0;
    }

    private char agentAt(int row, int col) {
        for (int i = 0; i < this.agentRows.length; i++) {
            if (this.agentRows[i] == row && this.agentCols[i] == col) {
                return (char) ('0' + i);
            }
        }
        return 0;
    }

    public Action[][] extractPlan() {
        Action[][] plan = new Action[this.g][];
        State state = this;
        while (state.jointAction != null) {
            plan[state.g - 1] = state.jointAction;
            state = state.parent;
        }
        return plan;
    }

    public Action[] extractPlanForCurrentAgent() {
        Action[] plan = new Action[this.g];
        State state = this;
        int currentAgentIndex =  this.agentRows.length - 1;
            // Iterate only if there is a valid plan (state.jointAction is not null)
            while (state.jointAction != null) {
                plan[state.g - 1] = state.jointAction[currentAgentIndex]; // Extract action for current agent
                state = state.parent;
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

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (int row = 0; row < this.walls.length; row++) {
            for (int col = 0; col < this.walls[row].length; col++) {
                if (this.boxes[row][col] > 0) {
                    s.append(this.boxes[row][col]);
                } else if (this.walls[row][col]) {
                    s.append("+");
                } else if (this.agentAt(row, col) != 0) {
                    s.append(this.agentAt(row, col));
                } else {
                    s.append(" ");
                }
            }
            s.append("\n");
        }
        return s.toString();
    }

    private int[][][][] distances;

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