package org.workcraft.plugins.parity;

import org.workcraft.dom.Container;
import org.workcraft.dom.Model;
import org.workcraft.dom.Node;
import org.workcraft.dom.math.AbstractMathModel;
import org.workcraft.dom.math.MathNode;
import org.workcraft.dom.math.MathConnection;
import org.workcraft.dom.references.HierarchyReferenceManager;
import org.workcraft.dom.references.NameManager;
import org.workcraft.plugins.parity.observers.SymbolConsistencySupervisor;
import org.workcraft.plugins.parity.OinkInputNode;
import org.workcraft.plugins.parity.OinkOutputNode;
import org.workcraft.serialisation.References;
import org.workcraft.utils.BackendUtils;
import org.workcraft.utils.Hierarchy;
import org.workcraft.types.MultiSet;
import org.workcraft.workspace.WorkspaceEntry;
import org.workcraft.utils.WorkspaceUtils;

import java.io.*;
import java.lang.StringBuilder;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Hashtable;

/** 
 * Class to model the entire Parity game. 
 * This is a subclass of the AbstractMathModel.
 *
 * A Parity game is a type of infinite graph game where the vertices are owned
 * by either Player 0 or Player 1. A token starts on a selected vertex, and the
 * player who owns a given vertex decides which edge the token travels across.
 * A strategy is followed (an edge is selected) by each player for each vertex 
 * they own, and this edge selection is guaranteed to be the same regardless of
 * context.
 *
 * The game ends once an infinite cycle has been identified, and then the group
 * of vertices that make up this are compared to see which infinitely occurring
 * vertex has the largest attached priority value. If it is even then Player 0
 * wins, and if it is odd then Player 1 wins. 
 *
 * In the graph, Player 0 vertices are identified by Circles, and Player 1
 * vertices are identified by Squares. Once the game has been solved, a vertex
 * will be coloured Blue if Player 0 wins, and will be coloured Red if Player 1
 * wins. If a player wins at a vertex and also owns it, then that means there is
 * a winning strategy for them from that vertex. This winning strategy edge will
 * be coloured blue or red for Players 0 or 1, respectively.
 *
 * A Backend tool is used to solve the Parity game. This tool is called Oink,
 * and was developed by Tom van Dijk. 
 * Link: https://github.com/trolando/oink
 *
 * Oink is not designed to be compatible with Windows natively, and as such this
 * plugin will only correctly run on Linux distros, MacOS, or Windows with WSL
 * or a Linux VM.
 */
public class Parity extends AbstractMathModel {

    public static final String EPSILON_SERIALISATION = "epsilon";

    /**
     * Empty constructor
     */
    public Parity() {
        this(null,null);
    }

    /**
     * Constructor that will instantiate a root Container, and References from
     * the AbstractMathModel superclass. This is to ensure all of the components
     * in the Parity game are well connected. Components in a Workcraft graph
     * are accessed in a similar structure to linked lists.
     * @param root    Abstract container to hold components
     * @param refs    Reference to all other linked components, managed by the 
     *                ReferenceManager
     */
    public Parity(Container root, References refs) {
        super(root, refs);
        new SymbolConsistencySupervisor(this).attach(getRoot());
    }

    /** 
     * Function that always returns false. Context is to ensure unused symbols
     * are not cached.
     * @return    false
     */
    public boolean keepUnusedSymbols() {
        return false;
    }

    /**
     * Generates a null symbol object, which is a MathNode subclass.
     * @return    null Symbol
     */
    public Symbol createSymbol(String name) {
        return createNode(name,null,Symbol.class);
    }

    /** 
     * Get the set of symbols within the MathModel, with no argument provided. 
     * This will be the Symbol components.
     * @return    Collection of Symbols in Model
     */
    public Collection<Symbol> getSymbols() {
        return Hierarchy.getDescendantsOfType(getRoot(), Symbol.class);
    }

    /** 
     * Get the set of symbols within the MathModel, with the container of 
     * components given as an argument.
     * @param container    Container of components
     * @return             Collection of Symbols in Model
     */
    public Collection<Symbol> getSymbols(Container container) {
        if (container == null) {
            container = getRoot();
        }
        return Hierarchy.getChildrenOfType(container, Symbol.class);
    }

    /** 
     * Get the set of Player 0 vertices within the MathModel, no arguments.
     * @return    Collection of Player 0 owned vertices within Model
     */
    public Collection<Player0> getPlayer0() {
        return Hierarchy.getDescendantsOfType(getRoot(), Player0.class);
    }

    /** 
     * Get the set of Player 0 vertices within the MathModel, with a symbol
     * provided as an argument. The symbol is used as a filter to only get 
     * descendants of whatever the symbol is inside the Player 0 vertex provided.
     * @param symbol    MathNode symbol to use as a filter as a temporary root
     * @return          Collection of Player 0 owned vertices within Model
     */
    public Collection<Player0> getPlayer0(final Symbol symbol) {
        return Hierarchy.getDescendantsOfType(getRoot(), Player0.class, 
            p0 -> p0.getSymbol() == symbol);
    }

    /** 
     * Get the set of Player 1 vertices within the MathModel, no arguments.
     * @return Collection of Player 1 owned vertices within Model
     */
    public Collection<Player1> getPlayer1() {
        return Hierarchy.getDescendantsOfType(getRoot(), Player1.class);
    }

    /** 
     * Get the set of Player 1 vertices within the MathModel, with a symbol
     * provided as an argument. The symbol is used as a filter to only get 
     * descendants of whatever the symbol is inside the Player 1 vertex provided.
     * @param symbol    MathNode symbol to use as a filter as a temporary root
     * @return          Collection of Player 1 owned vertices within Model
     */
    public Collection<Player1> getPlayer1(final Symbol symbol) {
        return Hierarchy.getDescendantsOfType(getRoot(), Player1.class, 
            p1 -> p1.getSymbol() == symbol);
    }

    /** 
     * Get the set of Connection (edge) components within the Parity game.
     * @return    Collection of edges within Model
     */
    public final Collection<MathConnection> getConnections() {
        return Hierarchy.getDescendantsOfType(getRoot(), MathConnection.class);
    }

    /**
     * Predicate function to check if the OS being used is either a Linux distro
     * or MacOS. Oink is not designed to work with Windows.
     * @return    true if OS name is either Mac or Linux
     */
    public boolean isMacLinux() {
        String osName = System.getProperty("os.name");
        osName = osName.toLowerCase();
        return (osName.contains("mac") || osName.contains("linux")) ? true : false;
    }

    /** 
     * Predicate function to ensure user has placed vertices on game graph.
     * If there are no vertices, owned by either player 0 or 1 on the graph,
     * false will be returned.
     * @return    true if there is at least one vertex in Model
     */
    public boolean isNonEmpty() {
        Collection<Player0> p0nodes = getPlayer0();
        Collection<Player1> p1nodes = getPlayer1();
        
        return (p0nodes.size() == 0 && p1nodes.size() == 0) ? false : true;
    }

    /**
     * Predicate function to ensure all vertices have a non-negative priority.
     * It is best practice to guarantee the vertex priorities are non-negative,
     * although it is possible to just increase the value of all priorities by
     * some uniform value such that the lowest negative number is >= 0.
     *
     * Returns true if All vertices have a priority that is 0 or larger.
     * @return    true if all vertices have priority of 0 or higher
     */
    public boolean isNonNegative() {
        Collection<Player0> p0nodes = getPlayer0();
        Collection<Player1> p1nodes = getPlayer1();
        Iterator<Player0> p0iter = p0nodes.iterator();
		Iterator<Player1> p1iter = p1nodes.iterator();

        while (p0iter.hasNext()) {
            Player0 tempNode = p0iter.next();
            if (tempNode.getPrio() < 0) {
                return false;
            }
        }

        while (p1iter.hasNext()) {
            Player1 tempNode = p1iter.next();
            if (tempNode.getPrio() < 0) {
                return false;
            }
        }

        return true;
    }

    /** 
     * Builds the Oink input format as a collection of Oink input nodes, which
     * are OinkInputNode objects. Each of these objects hold the automatically
     * assigned identifier 0-n, the priority of the vertex, which player owns
     * the vertex, and what vertices can be directly reached from the input node.
     * @return    ArrayList of OinkInputNodes. These are parsed to build the 
     *            text input for Oink.
     */
    public ArrayList<OinkInputNode> buildOinkInput() {
        Collection<Player0> p0nodes = getPlayer0();
        Collection<Player1> p1nodes = getPlayer1();
        Collection<MathConnection> edges = getConnections();
        Iterator<Player0> p0iter = p0nodes.iterator();
		Iterator<Player1> p1iter = p1nodes.iterator();
        Iterator<MathConnection> edgeIter = edges.iterator();
        Hashtable<String,Integer> nameToId = new Hashtable<String,Integer>();
        int nodeCounter = 0;    //Nodes must have identifiers of interval [0,N-1) 
                                //where N = amount of vertices in game

        /*
         * Initially gather ArrayLists of parameters from which to build the
         * OinkInputNodes. The index of the ArrayList refers to the identifier
         * of a given input node. Data will be collected from the Model to
         * fill these ArrayLists.
         */
        ArrayList<Integer> inputPriority = new ArrayList<Integer>();
        ArrayList<Boolean> ownedBy = new ArrayList<Boolean>();
        ArrayList<ArrayList<Integer> > outgoing = new ArrayList<ArrayList<Integer> >();
        ArrayList<OinkInputNode> inputNodes = new ArrayList<OinkInputNode>();

        while(p0iter.hasNext()) {
            Player0 tempNode = p0iter.next();
            tempNode.setId(nodeCounter);
            inputPriority.add(tempNode.getPrio());
            ownedBy.add(false);
            nameToId.put(getName(tempNode),nodeCounter++);
            ArrayList<Integer> tempArray = new ArrayList<Integer>();
            outgoing.add(tempArray);
        }

        while(p1iter.hasNext()) {
            Player1 tempNode = p1iter.next();
            tempNode.setId(nodeCounter);
            inputPriority.add(tempNode.getPrio());
            ownedBy.add(true);
            nameToId.put(getName(tempNode),nodeCounter++);
            ArrayList<Integer> tempArray = new ArrayList<Integer>();
            outgoing.add(tempArray);
        }

        while (edgeIter.hasNext()) {
            MathConnection tempEdge = edgeIter.next();
            MathNode tempFirst = tempEdge.getFirst();
            MathNode tempSecond = tempEdge.getSecond();
            Integer toBeAdded = nameToId.get(getName(tempSecond));
            Integer indexToAddTo = nameToId.get(getName(tempFirst));
            outgoing.get(indexToAddTo).add(toBeAdded);
        }

        /*
         * All of the information to build OinkInputNode objects has been 
         * collected; input nodes will now be built.
         */
        for (int inputNodeIter = 0; inputNodeIter < inputPriority.size(); 
            ++inputNodeIter) {
            
            OinkInputNode inputNode = new OinkInputNode
                (inputNodeIter, inputPriority.get(inputNodeIter), 
                    ownedBy.get(inputNodeIter), outgoing.get(inputNodeIter));
            inputNodes.add(inputNode);
        }

        return inputNodes;
    }

    /** 
     * Using the ArrayList of OinkInputNodes, generate the text input to be fed
     * into the Oink parity game solver backend.
     * 
     * More information about the input format Oink accepts can be found
     * at pp 34-43 in the following paper:
     * https://www.win.tue.nl/~timw/downloads/amc2014/pgsolver.pdf
     *
     * @param inputNodes    ArrayList of the OinkInputNodes
     * @return              Parity game represented as a String, adhering to 
     *                      the Oink input format.
     */
    public String printOinkInput (ArrayList<OinkInputNode> inputNodes) {
        
        StringBuilder outputBuilder = new StringBuilder();
        outputBuilder.append("parity " + inputNodes.size() + ";\n");
        for (int inputNodeIter = 0; inputNodeIter < inputNodes.size(); 
            ++inputNodeIter) {

            outputBuilder.append(inputNodes.get(inputNodeIter).getId() + " ");
            outputBuilder.append(inputNodes.get(inputNodeIter).getPrio() + " ");
            if (inputNodes.get(inputNodeIter).getOwnership() == false) {
                outputBuilder.append("0 ");
            } else {
                outputBuilder.append("1 ");
            }
            ArrayList<Integer> tempOutgoing = 
                inputNodes.get(inputNodeIter).getOutgoing();
            if (!tempOutgoing.isEmpty()) {
                Iterator<Integer> outgoingIter = tempOutgoing.iterator();
                while (outgoingIter.hasNext()) {
                    outputBuilder.append(outgoingIter.next() + ",");
                }
                outputBuilder.deleteCharAt(outputBuilder.length()-1);
            } else {
                outputBuilder.deleteCharAt(outputBuilder.length()-1);
            }
            outputBuilder.append(";\n");
        }

        return outputBuilder.toString();
    }

    /** 
     * Predicate function to check that every vertex in an Oink parity game have 
     * at least one successor (outgoing edge). Returns true if all vertices have
     * a successor.
     *
     * Oink deals exclusively with infinite games, although finite parity games
     * can exist.
     * @param inputNodes    ArrayList of OinkInputNodes gathered from Model.
     * @return              true if every vertex has at least one outgoing edge
     */
    public boolean isInfinite(ArrayList<OinkInputNode> inputNodes) {

        for (int inputNodeIter = 0; inputNodeIter < inputNodes.size(); 
            ++inputNodeIter) {

            if (inputNodes.get(inputNodeIter).getOutgoing().size()==0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Function to run Oink. The String built within printOinkInput is used to
     * build a temporary text file called oinkinput.txt. This is then fed into
     * Oink, and an output solution file is built called oinkoutput.sol. 
     *
     * This oinkoutput.sol file has the following syntax:
     * - First line will be paritysol N; where N is the amount of vertices in 
     *   the game.
     * - Every line after the first has the form X Y Z; where:
     *   X: Node identifier of interval [0,N-1)
     *   Y: Winner of game. 0 for player 0, 1 for player 1
     *   Z: OPTIONAL - If vertex is won by the player who owns it, show winning
     *      strategy with the vertex the token should travel to
     *
     * Note: For this to work, the Oink backend binary (for the respective OS) 
     *       must be built, placed inside a directory called 'Oink', and then 
     *       moved to:
     *       a) workcraft/dist/template/osx/Contents/Resources/tools
     *       b) workcraft/dist/template/linux/tools
     *
     *       As mentioned on Line 55, this plugin is not compatible with native
     *       Windows. WSL, Linux VM, Linux distro, or MacOS must be used.
     *
     * @param oinkInput    Input string built by printOinkInput
     * @return             String of STDOUT that Oink produces. oinkoutput.sol
     *                     will be used to generate the Oink output nodes.
     */
    public String runOink(String oinkInput) {
        String oinkBinaryPath = BackendUtils.getTemplateToolPath("Oink","oink");
        StringBuilder outputString = new StringBuilder();
        
        try {
            FileWriter inputFile = new FileWriter("oinkinput.txt");
            inputFile.write(oinkInput);
            inputFile.close();
            Process ps = Runtime.getRuntime().exec(oinkBinaryPath 
                + " -v -p oinkinput.txt oinkoutput.sol");
            BufferedReader bufRdr = new BufferedReader(
                new InputStreamReader(ps.getInputStream()));
            String currentLine;
            while ((currentLine = bufRdr.readLine()) != null) {
                outputString.append(currentLine);
                outputString.append("\n");
            }
            ps.waitFor();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        return outputString.toString();
    }

    /**
     * From the oinkoutput.sol text file generated by runOink, build the
     * ArrayList of OinkOutputNodes. These will then be used to correctly
     * colour the visual model.
     * @return    Parity Game modelled as an ArrayList of OinkOutputNodes
     */
    public ArrayList<OinkOutputNode> buildOutputNodes() {
        ArrayList<String> outputLines = new ArrayList<String>();
        File sol = new File("oinkoutput.sol");

        try {
            BufferedReader bufRdr = new BufferedReader(new FileReader(sol));
            String currentLine;
            //skip first line paritysol x;
            currentLine = bufRdr.readLine();
            while ((currentLine = bufRdr.readLine()) != null) {
                outputLines.add(currentLine);
            }
        } catch (Exception ex ) {
            ex.printStackTrace();
        }

        ArrayList<OinkOutputNode> outputNodes = new ArrayList<OinkOutputNode>();
        //Parse the lines into an arraylist of OinkOutputNodes
        for (int lineIter = 0; lineIter < outputLines.size(); ++lineIter) {
            String tempLine = outputLines.get(lineIter).substring(0, 
                outputLines.get(lineIter).length()-1);
            String[] words = tempLine.split(" ");
            Boolean tempWinner;
            if (words[1].equals("1")) {
                tempWinner = true;
            } else {
                tempWinner = false;
            }
            Integer tempStrategy = -1;
            if (words.length == 3) {
                tempStrategy = Integer.parseInt(words[2]);
            }
            
            OinkOutputNode tempOutputNode;
            if (tempStrategy != -1) {
                tempOutputNode = new OinkOutputNode(lineIter, tempWinner, 
                    tempStrategy);
            } else {
                tempOutputNode = new OinkOutputNode(lineIter, tempWinner);
            }
            outputNodes.add(tempOutputNode);
        }

        return outputNodes;
    }

    /**
     * Clean oinkinput.txt and oinkoutput.sol from directory
     */
    public void deleteTempFiles() {
        File oinkInput = new File("oinkinput.txt");
        File oinkOutput = new File("oinkoutput.sol");
        oinkInput.delete();
        oinkOutput.delete();
    }

    /**
     * Overridden function used to reparent all vertices within the list as
     * necessary. This will need to be repeated for vertices owned by Player 0,
     * and also vertices owned by Player 1.
     * @param dstContainer    Abstract destination container of what the model
     *                        will become.
     * @param srcModel        Source model of vertices and edges; what the model
                              was before reparenting.
     * @param srcRoot         Source root component.
     * @param srcChildren     Source children of root component.
     */
    @Override
    public boolean reparent(Container dstContainer, Model srcModel, 
            Container srcRoot, Collection<? extends MathNode> srcChildren) {
        if (srcModel == null) {
            srcModel = this;
        }
        HierarchyReferenceManager refManager = getReferenceManager();
        NameManager nameManager = refManager.getNameManager(null);
        for (MathNode srcNode: srcChildren) {
            if (srcNode instanceof Player0) {
                Player0 srcPlayer0 = (Player0) srcNode;
                Symbol dstSymbol = null;
                Symbol srcSymbol = srcPlayer0.getSymbol();
                if (srcSymbol != null) {
                    String symbolName = srcModel.getNodeReference(srcSymbol);
                    Node dstNode = getNodeByReference(symbolName);
                    if (dstNode instanceof Symbol) {
                        dstSymbol = (Symbol) dstNode;
                    } else {
                        if (dstNode != null) {
                            symbolName = nameManager.getDerivedName(null, symbolName);
                        }
                        dstSymbol = createSymbol(symbolName);
                    }
                }
                srcPlayer0.setSymbol(dstSymbol);
            } else if (srcNode instanceof Player1) {
                Player1 srcPlayer1 = (Player1) srcNode;
                Symbol dstSymbol = null;
                Symbol srcSymbol = srcPlayer1.getSymbol();
                if (srcSymbol != null) {
                    String symbolName = srcModel.getNodeReference(srcSymbol);
                    Node dstNode = getNodeByReference(symbolName);
                    if (dstNode instanceof Symbol) {
                        dstSymbol = (Symbol) dstNode;
                    } else {
                        if (dstNode != null) {
                            symbolName = nameManager.getDerivedName(null, symbolName);
                        }
                        dstSymbol = createSymbol(symbolName);
                    }
                }
                srcPlayer1.setSymbol(dstSymbol);
            }
        }
        return super.reparent(dstContainer, srcModel, srcRoot, srcChildren);
    }
}