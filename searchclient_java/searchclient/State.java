package searchclient;

// import searchclient.Action;
// import searchclient.ActionType;
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
    public static Color[] boxColors;
    /*
     * The box colors are indexed alphabetically. So this.boxColors[0] is the color
     * of A boxes,
     * this.boxColor[1] is the color of B boxes, etc.
     */
    public final State parent;
    public Action[] jointAction;
    public final int g;
    private int hash = 0;
    private int currentAgentIndex;

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
        this.jointAction = new Action[agentRows.length];
        this.g = 0;
        // **Change:** Initialize jointAction
        this.jointAction = new Action[agentRows.length]; // Initialize with the number of agents
        System.err.println("State Constructor: jointAction.length = " + this.jointAction.length);
        for (int i = 0; i < agentRows.length; i++) {
            this.jointAction[i] = Action.NoOp; // Set each element to NoOp
        }
        System.err.println("State Constructor: jointAction = " + Arrays.toString(this.jointAction));
    }

    // Constructs the state resulting from applying jointAction in parent.
    // Precondition: Joint action must be applicable and non-conflicting in parent
    // state.
    private State(State parent, Action[] jointAction, int currentAgentIndex) {
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
        this.currentAgentIndex = currentAgentIndex;
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

    public boolean isGoalStateForAgent(int agentIndex) {
        // Check if the agent has reached its goal position
        if (!(this.agentRows[agentIndex] == this.goals[this.agentRows[agentIndex]][this.agentCols[agentIndex]] - '0' &&
                this.agentCols[agentIndex] == this.goals[this.agentRows[agentIndex]][this.agentCols[agentIndex]]
                        - '0')) {
            return false;
        }
        // Check if the agent has moved all its boxes to their goal locations
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

                    if (conflicts(this.jointAction[j], previousPlans[agent - 1][i], j, agent - 1)) {
                        // Concede to the previous agent's plan
                        this.jointAction[j] = Action.NoOp;
                        break; // Move to the next action in the previous plan
                    }
                }
            }
        }
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
        return new int[]{agentDestinationRow, agentDestinationCol, boxRow, boxCol};
    }
    // Helper function to check if two actions conflict
    private boolean conflicts(Action action1, Action action2, int agentIndex1, int agentIndex2) {
        int agent1Row = this.agentRows[agentIndex1];
        int agent1Col = this.agentCols[agentIndex1];
        int agent2Row = this.agentRows[agentIndex2];
        int agent2Col = this.agentCols[agentIndex2];    

        int[] positions1 = calculatePositions(action1, agent1Row, agent1Col);
        int[] positions2 = calculatePositions(action2, agent2Row, agent2Col);

        // Check for conflicts
        if ( action1.type == ActionType.NoOp || action2.type == ActionType.NoOp ) {
            return false; // No conflict if either action is NoOp 
        }
        if(action1.type == ActionType.Move && action2.type == ActionType.Move && positions1[0] == positions2[0] && positions1[1] == positions2[1]){
            return true; // Both agents attempt to occupy the same cell        
        }        
        if(action1.type == ActionType.Pull && action2.type == ActionType.Pull && positions1[2] == positions2[2] && positions1[3] == positions2[3]){
            return true; // Both agents attempt to pull the same box        
        }
        if(action1.type == ActionType.Push && action2.type == ActionType.Push && positions1[0] == positions2[0] && positions1[1] == positions2[1]){
            return true; // Both agents attempt to push the same box
        }
        if(action1.type == ActionType.Pull && action2.type == ActionType.Push && (positions1[2] == positions2[0] && positions1[3] == positions2[1])){
            return true; // First agent tries to pull a box that the other tries to push
        }
        if(action1.type == ActionType.Push && action2.type == ActionType.Pull && (positions1[0] == positions2[2] && positions1[1] == positions2[3])){
            return true; // First agent tries to push a box that the other tries to pull
        }
        if(action1.type == ActionType.Move && action2.type == ActionType.Push && (positions1[0] == positions2[2] && positions1[1] == positions2[3])){
            return true; // An agent tries to move where a box is being pushed in
        }            
        return false;    }

 
 
    // Function to resolve conflicts with previously generated plans
    public ArrayList<State> getExpandedStatesSequential(Action[][] previousPlans, int currentAgentIndex) {
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
                System.err.println("getExpandedStatesSequential: jointAction.length = " + jointAction.length);
                System.err.println("createStaticStateForAgent: agentState.jointAction = "
                        + Arrays.toString(agentState.jointAction));
                // **Change:** Create a new State object and resolve conflicts
                State childState = new State(agentState, jointAction, currentAgentIndex);
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
        agentState.currentAgentIndex = agentIndex;
        agentState.jointAction = new Action[agentState.agentRows.length];
        System.err
                .println("createStaticStateForAgent: agentState.jointAction.length = " + agentState.jointAction.length);
        System.err.println(
                "createStaticStateForAgent: agentState.jointAction = " + Arrays.toString(agentState.jointAction));

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

    public Action[] extractPlanForCurrentAgent() {
        Action[] plan = new Action[this.g];
        State state = this;
        // **Change:** Use the currentAgentIndex
        int currentAgentIndex = this.currentAgentIndex;
        System.err.println("extractPlanForCurrentAgent: state.jointAction.length = " + state.jointAction.length);
        // Iterate only if there is a valid plan (state.jointAction is not null)
        while (state.jointAction != null) {
            System.err.println("Length of jointAction is : " + state.jointAction.length);
            System.err.println("Current Agent Index is : " + currentAgentIndex);
            System.err.println("joint action is : " + state.jointAction);
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