package org.workcraft.plugins.circuit.interop;

import org.workcraft.Framework;
import org.workcraft.dom.Node;
import org.workcraft.dom.hierarchy.NamespaceHelper;
import org.workcraft.dom.hierarchy.NamespaceProvider;
import org.workcraft.dom.math.PageNode;
import org.workcraft.dom.references.FileReference;
import org.workcraft.dom.references.HierarchyReferenceManager;
import org.workcraft.dom.references.NameManager;
import org.workcraft.dom.references.ReferenceHelper;
import org.workcraft.exceptions.*;
import org.workcraft.formula.BooleanFormula;
import org.workcraft.formula.BooleanVariable;
import org.workcraft.formula.FormulaUtils;
import org.workcraft.formula.bdd.BddManager;
import org.workcraft.formula.jj.BooleanFormulaParser;
import org.workcraft.formula.jj.ParseException;
import org.workcraft.gui.MainWindow;
import org.workcraft.gui.properties.PropertyHelper;
import org.workcraft.interop.Importer;
import org.workcraft.plugins.builtin.settings.DebugCommonSettings;
import org.workcraft.plugins.circuit.*;
import org.workcraft.plugins.circuit.Contact.IOType;
import org.workcraft.plugins.circuit.expression.Expression;
import org.workcraft.plugins.circuit.expression.Literal;
import org.workcraft.plugins.circuit.genlib.*;
import org.workcraft.plugins.circuit.jj.expression.ExpressionParser;
import org.workcraft.plugins.circuit.utils.*;
import org.workcraft.plugins.circuit.verilog.*;
import org.workcraft.plugins.stg.Mutex;
import org.workcraft.plugins.stg.Signal;
import org.workcraft.plugins.stg.StgSettings;
import org.workcraft.plugins.stg.Wait;
import org.workcraft.utils.DialogUtils;
import org.workcraft.utils.FileUtils;
import org.workcraft.utils.LogUtils;
import org.workcraft.utils.TextUtils;
import org.workcraft.workspace.ModelEntry;
import org.workcraft.workspace.WorkspaceEntry;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.*;

public class VerilogImporter implements Importer {

    private static final String VERILOG_IMPORT_TITLE = "Import of Verilog netlist";

    private final boolean celementAssign;
    private final boolean sequentialAssign;

    private Map<VerilogModule, String> moduleFileNames = null;

    private static class AssignGate {
        public final String outputName;
        public final String setFunction;
        public final String resetFunction;
        public final HashMap<String, String> connections; // (portName -> netName)

        AssignGate(String outputName, String setFunction, String resetFunction, HashMap<String, String> connections) {
            this.outputName = outputName;
            this.setFunction = setFunction;
            this.resetFunction = resetFunction;
            this.connections = connections;
        }
    }

    private static class Net {
        public FunctionContact source = null;
        public HashSet<FunctionContact> sinks = new HashSet<>();
        public HashSet<FunctionContact> undefined = new HashSet<>();
    }

    // Default constructor is required for PluginManager -- it is called via reflection.
    public VerilogImporter() {
        this(true, false);
    }

    public VerilogImporter(boolean celementAssign, boolean sequentialAssign) {
        this.celementAssign = celementAssign;
        this.sequentialAssign = sequentialAssign;
    }

    @Override
    public VerilogFormat getFormat() {
        return VerilogFormat.getInstance();
    }

    @Override
    public ModelEntry importFrom(InputStream in)
            throws OperationCancelledException, DeserialisationException  {

        moduleFileNames = null;
        Collection<VerilogModule> verilogModules = VerilogUtils.importVerilogModules(in);
        if ((verilogModules == null) || verilogModules.isEmpty()) {
            throw new DeserialisationException("No Verilog modules could be imported");
        }

        Set<String> undefinedModules = VerilogUtils.getUndefinedModules(verilogModules);
        if (!undefinedModules.isEmpty()) {
            String msg = TextUtils.wrapMessageWithItems("Verilog netlist refers to undefined module", undefinedModules)
                    + "\n\nInstances of these modules may be interpreted incorrectly due to missing information about input/output pins."
                    + "\n\nPossible causes of the problem:"
                    + "\n" + PropertyHelper.BULLET_PREFIX + "incorrect path to gate library GenLib file"
                    + "\n" + PropertyHelper.BULLET_PREFIX + "incorrect setup for MUTEX and WAIT elements"
                    + "\n" + PropertyHelper.BULLET_PREFIX + "missing module definition in hierarchical Verilog netlist"
                    + "\n" + PropertyHelper.BULLET_PREFIX + "incorrect use of substitution rules for Verilog import"
                    + "\n\nProceed with Verilog import anyway?";

            if (!DialogUtils.showConfirmWarning(msg, VERILOG_IMPORT_TITLE, false)) {
                throw new OperationCancelledException();
            }
        }

        Circuit circuit = (verilogModules.size() == 1)
                ? createCircuit(verilogModules.iterator().next())
                : createCircuitHierarchy(verilogModules);

        return new ModelEntry(new CircuitDescriptor(), circuit);
    }

    public Circuit createCircuit(VerilogModule verilogModule) {
        return createCircuit(verilogModule, Collections.emptySet());
    }

    public Circuit createCircuit(VerilogModule verilogModule, Collection<Mutex> mutexes) {
        return createCircuit(verilogModule, Collections.singletonList(verilogModule), mutexes);
    }

    private Circuit createCircuitHierarchy(Collection<VerilogModule> verilogModules)
            throws DeserialisationException, OperationCancelledException {

        if (Framework.getInstance().isInGuiMode()) {
            return createCircuitHierarchyGui(verilogModules);
        } else {
            return createCircuitHierarchyAuto(verilogModules);
        }
    }

    private Circuit createCircuitHierarchyGui(Collection<VerilogModule> verilogModules)
            throws OperationCancelledException {

        MainWindow mainWindow = Framework.getInstance().getMainWindow();
        ImportVerilogDialog dialog = new ImportVerilogDialog(mainWindow, verilogModules);
        if (!dialog.reveal()) {
            throw new OperationCancelledException();
        }
        VerilogModule topVerilogModule = dialog.getTopModule();
        File dir = dialog.getDirectory();
        Set<VerilogModule> descendantModules = VerilogUtils.getDescendantModules(topVerilogModule, verilogModules);
        moduleFileNames = dialog.getModuleFileNames();
        Collection<String> existingSaveFilePaths = getExistingSaveFilePaths(descendantModules, dir);
        if (!existingSaveFilePaths.isEmpty()) {
            String delimiter = "\n" + PropertyHelper.BULLET_PREFIX;
            String msg = "The following files already exist:" + delimiter
                    + String.join(delimiter, existingSaveFilePaths)
                    + "\n\nOverwrite these files and proceed with import?";

            if (!DialogUtils.showConfirmWarning(msg, VERILOG_IMPORT_TITLE, false)) {
                throw new OperationCancelledException();
            }
        }
        return createCircuitHierarchy(topVerilogModule, descendantModules, dir);
    }

    private Circuit createCircuitHierarchyAuto(Collection<VerilogModule> verilogModules)
            throws DeserialisationException, OperationCancelledException {

        VerilogModule topVerilogModule = VerilogUtils.getTopModule(verilogModules);
        Set<VerilogModule> descendantModules = VerilogUtils.getDescendantModules(topVerilogModule, verilogModules);
        File dir = Framework.getInstance().getWorkingDirectory();
        moduleFileNames = VerilogUtils.getModuleToFileMap(verilogModules);
        Collection<String> existingSaveFilePaths = getExistingSaveFilePaths(descendantModules, dir);
        if (!existingSaveFilePaths.isEmpty()) {
            String msg = "Import of circuit hierarchy overwrites the following files:\n"
                    + String.join("\n", existingSaveFilePaths);

            LogUtils.logWarning(msg);
        }
        return createCircuitHierarchy(topVerilogModule, descendantModules, dir);
    }

    private Collection<String> getExistingSaveFilePaths(Set<VerilogModule> descendantModules, File dir) {
        Collection<String> result = new HashSet<>();
        for (VerilogModule module : descendantModules) {
            String fileName = moduleFileNames.get(module);
            File file = new File(dir, fileName);
            if (file.exists()) {
                result.add(file.getAbsolutePath());
            }
        }
        return result;
    }

    private Circuit createCircuitHierarchy(VerilogModule topVerilogModule,
            Collection<VerilogModule> descendantModules, File dir) throws OperationCancelledException {

        Circuit circuit = createCircuit(topVerilogModule, descendantModules, Collections.emptySet());

        Map<Circuit, String> circuitFileNames = new HashMap<>();
        for (VerilogModule verilogModule : descendantModules) {
            Circuit descendantCircuit = createCircuit(verilogModule, descendantModules, Collections.emptySet());
            if (moduleFileNames != null) {
                circuitFileNames.put(descendantCircuit, moduleFileNames.get(verilogModule));
            }
        }
        adjustModuleRefinements(circuit, circuitFileNames, dir);
        return circuit;
    }

    private void adjustModuleRefinements(Circuit topCircuit, Map<Circuit, String> circuitFileNames, File dir) {
        Framework framework = Framework.getInstance();
        for (Circuit circuit : circuitFileNames.keySet()) {
            ModelEntry me = new ModelEntry(new CircuitDescriptor(), circuit);
            WorkspaceEntry we = framework.createWork(me, circuit.getTitle());
            try {
                File file = new File(dir, circuitFileNames.get(circuit));
                framework.saveWork(we, file);
            } catch (SerialisationException e) {
                e.printStackTrace();
            }
        }
        String base = FileUtils.getFullPath(dir);
        for (FunctionComponent component : topCircuit.getFunctionComponents()) {
            FileReference refinement = component.getRefinement();
            if (refinement != null) {
                File file = FileUtils.getFileByPathAndBase(refinement.getPath(), base);
                String path = FileUtils.getFullPath(file);
                refinement.setPath(path);
            }
        }
    }

    private Circuit createCircuit(VerilogModule verilogModule, Collection<VerilogModule> descendantModules,
            Collection<Mutex> mutexes) {

        Circuit circuit = new Circuit();
        circuit.setTitle(verilogModule.name);
        HashMap<VerilogInstance, FunctionComponent> instanceComponentMap = new HashMap<>();
        HashMap<String, Net> nets = createPorts(circuit, verilogModule);
        for (VerilogAssign verilogAssign : verilogModule.assigns) {
            createAssignGate(circuit, verilogAssign, nets);
        }
        for (VerilogInstance verilogInstance : verilogModule.instances) {
            SubstitutionRule substitutionRule = null;
            String moduleName = verilogInstance.moduleName;
            Gate gate = createPrimitiveGate(verilogInstance);
            if (gate == null) {
                Library library = LibraryManager.getLibrary();
                if (library != null) {
                    Map<String, SubstitutionRule> substitutionRules = LibraryManager.getImportSubstitutionRules();
                    substitutionRule = substitutionRules.get(moduleName);
                    String msg = "Processing instance '" + verilogInstance.name + "' in module '" + verilogModule.name + "': ";
                    String gateName = SubstitutionUtils.getModuleSubstitutionName(moduleName, substitutionRule, msg);
                    gate = library.get(gateName);
                }
            }

            FunctionComponent component = null;
            if (gate != null) {
                component = createLibraryGate(circuit, verilogInstance, nets, gate, substitutionRule);
            } else if (VerilogUtils.isWaitInstance(moduleName)) {
                Wait waitInstance = instanceToWaitWithType(verilogInstance);
                component = createWait(circuit, waitInstance, nets);
            } else if (VerilogUtils.isMutexInstance(moduleName)) {
                Mutex mutexInstance = instanceToMutexWithProtocol(verilogInstance, mutexes);
                component = createMutex(circuit, mutexInstance, nets);
            } else {
                component = createModuleInstance(circuit, verilogInstance, nets, descendantModules);
            }
            if (component != null) {
                instanceComponentMap.put(verilogInstance, component);
            }
        }
        insertMutexes(mutexes, circuit, nets);
        createConnections(circuit, nets);
        Map<String, Boolean> signalStates = getSignalState(verilogModule.initialState);
        setInitialState(nets, signalStates);
        setZeroDelayAttribute(instanceComponentMap);
        checkImportResult(circuit, nets.keySet(), signalStates.keySet());
        return circuit;
    }

    private Map<String, Boolean> getSignalState(Map<VerilogNet, Boolean> netStates) {
        Map<String, Boolean> result = new HashMap<>();
        if (netStates != null) {
            for (Map.Entry<VerilogNet, Boolean> entry : netStates.entrySet()) {
                VerilogNet verilogNet = entry.getKey();
                Boolean state = entry.getValue();
                String netName = VerilogUtils.getNetBusSuffixName(verilogNet);
                result.put(netName, state);
            }
        }
        return result;
    }

    private void checkImportResult(Circuit circuit, Set<String> usedSignals,
            Set<String> initialisedSignals) {

        String msg = "";
        // Check circuit for no components
        if (circuit.getFunctionComponents().isEmpty()) {
            msg += "\n" + PropertyHelper.BULLET_PREFIX + "No components";
        }
        // Check circuit for hanging contacts
        Set<String> hangingSignals = VerificationUtils.getHangingSignals(circuit);
        if (!hangingSignals.isEmpty()) {
            msg += TextUtils.wrapMessageWithItems("\n" + PropertyHelper.BULLET_PREFIX
                    + "Hanging contact", hangingSignals);
        }
        // Check circuit for uninitialised signals
        Set<String> uninitialisedSignals = new HashSet<>(usedSignals);
        uninitialisedSignals.addAll(VerificationUtils.getHangingDriverSignals(circuit));
        uninitialisedSignals.removeAll(initialisedSignals);
        if (!uninitialisedSignals.isEmpty()) {
            msg += TextUtils.wrapMessageWithItems("\n" + PropertyHelper.BULLET_PREFIX
                    + "Missing initial state declaration (assuming low) for signal", uninitialisedSignals);
        }
        // Check circuit for uninitialised signals
        Set<String> unusedSignals = new HashSet<>(initialisedSignals);
        unusedSignals.removeAll(usedSignals);
        if (!unusedSignals.isEmpty()) {
            msg += TextUtils.wrapMessageWithItems("\n" + PropertyHelper.BULLET_PREFIX
                    + "Initial state declaration for unused signal", unusedSignals);
        }
        // Produce warning
        if (!msg.isEmpty()) {
            String title = circuit.getTitle().isEmpty() ? "" : "'" + circuit.getTitle() + "' ";
            DialogUtils.showWarning("The imported circuit " + title + "has the following issues:" + msg);
        }
    }

    private Wait instanceToWaitWithType(VerilogInstance verilogInstance) {
        Wait module = ArbitrationUtils.getWaitModule(verilogInstance.moduleName);
        if ((module == null) || (module.name == null)) {
            return null;
        }
        String name = verilogInstance.name;
        Signal sig = getPinConnectedSignal(verilogInstance, module.sig.name, 0);
        Signal ctrl = getPinConnectedSignal(verilogInstance, module.ctrl.name, 2);
        Signal san = getPinConnectedSignal(verilogInstance, module.san.name, 3);

        Wait result = new Wait(name, sig, ctrl, san);
        result.setType(module.getType());
        return result;
    }

    private Mutex instanceToMutexWithProtocol(VerilogInstance verilogInstance, Collection<Mutex> mutexes) {
        Mutex module = CircuitSettings.parseMutexData();
        if ((module == null) || (module.name == null)) {
            return null;
        }
        String name = verilogInstance.name;
        Signal r1 = getPinConnectedSignal(verilogInstance, module.r1.name, 0);
        Signal g1 = getPinConnectedSignal(verilogInstance, module.g1.name, 1);
        Signal r2 = getPinConnectedSignal(verilogInstance, module.r2.name, 2);
        Signal g2 = getPinConnectedSignal(verilogInstance, module.g2.name, 3);

        // Get fall-back protocol from default preferences and module name
        Mutex.Protocol defaultProtocol = StgSettings.getMutexProtocol();
        if (ArbitrationUtils.appendMutexProtocolSuffix(module.name, Mutex.Protocol.LATE).equals(verilogInstance.moduleName)) {
            defaultProtocol = Mutex.Protocol.LATE;
        } else if (ArbitrationUtils.appendMutexProtocolSuffix(module.name, Mutex.Protocol.EARLY).equals(verilogInstance.moduleName)) {
            defaultProtocol = Mutex.Protocol.EARLY;
        }

        Mutex.Protocol protocol = mutexes.stream()
                .filter(mutex -> (mutex.name != null) && mutex.name.equals(name))
                .findFirst()
                .map(Mutex::getProtocol)
                .orElse(defaultProtocol);

        Mutex result = new Mutex(name, r1, g1, r2, g2);
        result.setProtocol(protocol);
        return result;
    }

    private Signal getPinConnectedSignal(VerilogInstance verilogInstance, String portName, int portIndexIfNoPortName) {
        VerilogConnection verilogConnection = null;
        boolean useNamedConnections = true;
        for (VerilogConnection connection : verilogInstance.connections) {
            if (connection.name == null) {
                useNamedConnections = false;
                break;
            } else if (connection.name.equals(portName)) {
                verilogConnection = connection;
                break;
            }
        }
        if (!useNamedConnections && (portIndexIfNoPortName < verilogInstance.connections.size())) {
            verilogConnection = verilogInstance.connections.get(portIndexIfNoPortName);
        }
        return verilogConnection == null ? null
                : new Signal(VerilogUtils.getNetBusSuffixName(verilogConnection.net), Signal.Type.INTERNAL);
    }

    private FunctionComponent createAssignGate(Circuit circuit, VerilogAssign verilogAssign, HashMap<String, Net> nets) {
        final FunctionComponent component = new FunctionComponent();
        circuit.add(component);
        String netName = VerilogUtils.getNetBusSuffixName(verilogAssign.net);
        reparentAndRenameComponent(circuit, component, netName);

        AssignGate assignGate;
        if ((celementAssign && isCelementAssign(verilogAssign))
                || (sequentialAssign && isSequentialAssign(verilogAssign))) {

            assignGate = createSequentialAssignGate(verilogAssign);
        } else {
            assignGate = createCombinationalAssignGate(verilogAssign);
        }
        FunctionContact outContact = null;
        for (Map.Entry<String, String> connection : assignGate.connections.entrySet()) {
            FunctionContact contact = new FunctionContact();
            if (connection.getKey().equals(assignGate.outputName)) {
                contact.setIOType(IOType.OUTPUT);
                outContact = contact;
            } else {
                contact.setIOType(IOType.INPUT);
            }
            component.add(contact);
            if (connection.getKey() != null) {
                circuit.setName(contact, connection.getKey());
            }
            Net net = getOrCreateNet(connection.getValue(), nets);
            if (net != null) {
                if (contact.isOutput()) {
                    net.source = contact;
                } else {
                    net.sinks.add(contact);
                }
            }
        }

        if (outContact != null) {
            try {
                BooleanFormula setFormula = CircuitUtils.parsePinFunction(circuit, component, assignGate.setFunction);
                outContact.setSetFunctionQuiet(setFormula);
                BooleanFormula resetFormula = CircuitUtils.parsePinFunction(circuit, component, assignGate.resetFunction);
                outContact.setResetFunctionQuiet(resetFormula);
            } catch (org.workcraft.formula.jj.ParseException e) {
                throw new RuntimeException(e);
            }
        }
        return component;
    }

    private boolean isSequentialAssign(VerilogAssign verilogAssign) {
        String netName = VerilogUtils.getNetBusSuffixName(verilogAssign.net);
        String formula = VerilogUtils.getFormulaWithBusSuffixNames(verilogAssign.formula);
        Expression expression = convertFormulaToExpression(formula);
        LinkedList<Literal> literals = new LinkedList<>(expression.getLiterals());
        return literals.stream().anyMatch(literal -> (netName != null) && netName.equals(literal.name));
    }

    private boolean isCelementAssign(VerilogAssign verilogAssign) {
        try {
            BooleanFormula formula = BooleanFormulaParser.parse(VerilogUtils.getFormulaWithBusSuffixNames(verilogAssign.formula));
            List<BooleanVariable> variables = FormulaUtils.extractOrderedVariables(formula);
            if (variables.size() != 3) {
                return false;
            }
            String netName = VerilogUtils.getNetBusSuffixName(verilogAssign.net);
            if (variables.stream().noneMatch(var -> (netName != null) && netName.equals(var.getLabel()))) {
                return false;
            }
            BooleanVariable aVar = variables.get(0);
            BooleanVariable bVar = variables.get(1);
            BooleanVariable cVar = variables.get(2);
            return new BddManager().equal(FormulaUtils.createMaj(aVar, bVar, cVar), formula);
        } catch (ParseException e) {
            return false;
        }
    }

    private AssignGate createCombinationalAssignGate(VerilogAssign verilogAssign) {
        String formula = VerilogUtils.getFormulaWithBusSuffixNames(verilogAssign.formula);
        Expression expression = convertFormulaToExpression(formula);
        int index = 0;
        HashMap<String, String> connections = new HashMap<>(); // (port -> net)
        String outputName = VerilogUtils.getPrimitiveGatePinName(0);
        String netName = VerilogUtils.getNetBusSuffixName(verilogAssign.net);
        connections.put(outputName, netName);
        LinkedList<Literal> literals = new LinkedList<>(expression.getLiterals());
        Collections.reverse(literals);
        for (Literal literal: literals) {
            String literalName = literal.name;
            String name = VerilogUtils.getPrimitiveGatePinName(++index);
            literal.name = name;
            connections.put(name, literalName);
        }
        return new AssignGate(outputName, expression.toString(), null, connections);
    }

    private AssignGate createSequentialAssignGate(VerilogAssign verilogAssign) {
        String formula = VerilogUtils.getFormulaWithBusSuffixNames(verilogAssign.formula);
        Expression expression = convertFormulaToExpression(formula);
        String function = expression.toString();

        String netName = VerilogUtils.getNetBusSuffixName(verilogAssign.net);
        String setFunction = ExpressionUtils.extractSetExpression(function, netName);
        Expression setExpression = convertFormulaToExpression(setFunction);

        String resetFunction = ExpressionUtils.extractResetExpression(function, netName);
        Expression resetExpression = convertFormulaToExpression(resetFunction);
        if (DebugCommonSettings.getVerboseImport()) {
            LogUtils.logInfo("Extracting SET and RESET from assign " + netName + " = " + formula);
            LogUtils.logInfo("  Function: " + function);
            LogUtils.logInfo("  Set function: " + setFunction);
            LogUtils.logInfo("  Reset function: " + resetFunction);
        }
        HashMap<String, String> connections = new HashMap<>();
        String outputName = VerilogUtils.getPrimitiveGatePinName(0);
        connections.put(outputName, netName);

        LinkedList<Literal> literals = new LinkedList<>();
        literals.addAll(setExpression.getLiterals());
        literals.addAll(resetExpression.getLiterals());
        Collections.reverse(literals);
        int index = 0;
        HashMap<String, String> netToPortMap = new HashMap<>();
        for (Literal literal: literals) {
            String literalName = literal.name;
            String name = netToPortMap.get(literalName);
            if (name == null) {
                name = VerilogUtils.getPrimitiveGatePinName(++index);
                netToPortMap.put(literalName, name);
            }
            literal.name = name;
            connections.put(name, literalName);
        }
        return new AssignGate(outputName, setExpression.toString(), resetExpression.toString(), connections);
    }

    private Expression convertFormulaToExpression(String formula) {
        InputStream expressionStream = new ByteArrayInputStream(formula.getBytes());
        ExpressionParser expressionParser = new ExpressionParser(expressionStream);
        if (DebugCommonSettings.getParserTracing()) {
            expressionParser.enable_tracing();
        } else {
            expressionParser.disable_tracing();
        }
        Expression expression = null;
        try {
            expression = expressionParser.parseExpression();
        } catch (org.workcraft.plugins.circuit.jj.expression.ParseException e) {
            LogUtils.logWarning("Could not parse assign expression '" + formula + "'.");
        }
        return expression;
    }

    private HashMap<String, Net> createPorts(Circuit circuit, VerilogModule verilogModule) {
        HashMap<String, Net> nets = new HashMap<>();
        for (VerilogPort verilogPort: verilogModule.ports) {
            List<String> portNames = getPortNames(verilogPort);
            if (verilogPort.range != null) {
                LogUtils.logInfo("Bus " + verilogPort.name + verilogPort.range + " is split to nets: "
                        + String.join(", ", portNames));
            }
            for (String portName: portNames) {
                createPort(circuit, nets, portName, verilogPort.isInput());
            }
        }
        return nets;
    }

    private void createPort(Circuit circuit, HashMap<String, Net> nets, String portName, boolean isInput) {
        FunctionContact contact = circuit.createNodeWithHierarchy(portName, circuit.getRoot(), FunctionContact.class);
        if (contact != null) {
            if (isInput) {
                contact.setIOType(IOType.INPUT);
            } else {
                contact.setIOType(IOType.OUTPUT);
            }
            Net net = getOrCreateNet(portName, nets);
            if (net != null) {
                if (isInput) {
                    net.source = contact;
                } else {
                    net.sinks.add(contact);
                }
            }
        }
    }

    private List<String> getPortNames(VerilogPort verilogPort) {
        List<String> result = new ArrayList<>();
        if (verilogPort.range == null) {
            result.add(verilogPort.name);
        } else {
            int first = verilogPort.range.getFirst();
            int second = verilogPort.range.getSecond();
            if (first < second) {
                for (int i = first; i <= second; i++) {
                    VerilogNet verilogNet = new VerilogNet(verilogPort.name, i);
                    result.add(VerilogUtils.getNetBusSuffixName(verilogNet));
                }
            } else {
                for (int i = first; i >= second; i--) {
                    VerilogNet verilogNet = new VerilogNet(verilogPort.name, i);
                    result.add(VerilogUtils.getNetBusSuffixName(verilogNet));
                }
            }
        }
        return result;
    }

    private Gate createPrimitiveGate(VerilogInstance verilogInstance) {
        String operator = VerilogUtils.getPrimitiveOperator(verilogInstance.moduleName);
        if (operator == null) {
            return null;
        }
        StringBuilder expression = new StringBuilder();
        int index;
        for (index = 0; index < verilogInstance.connections.size(); index++) {
            if (index > 0) {
                String pinName = VerilogUtils.getPrimitiveGatePinName(index);
                if (expression.length() > 0) {
                    expression.append(operator);
                }
                expression.append(pinName);
            }
        }
        if (!VerilogUtils.getPrimitivePolarity(verilogInstance.moduleName)) {
            if (index > 1) {
                expression = new StringBuilder("(" + expression + ")");
            }
            expression.insert(0, "!");
        }
        Function function = new Function(VerilogUtils.getPrimitiveGatePinName(0), expression.toString());
        return new Gate("", 0.0, function, null, true);
    }

    private FunctionComponent createLibraryGate(Circuit circuit, VerilogInstance verilogInstance,
            HashMap<String, Net> nets, Gate gate, SubstitutionRule substitutionRule) {

        String msg = "Processing instance '" + verilogInstance.name + "' in module '" + circuit.getTitle() + "': ";
        FunctionComponent component = GenlibUtils.instantiateGate(gate, verilogInstance.name, circuit);
        List<FunctionContact> orderedContacts = getGateOrderedContacts(component);
        int index = -1;
        for (VerilogConnection verilogConnection : verilogInstance.connections) {
            index++;
            if (verilogConnection == null) {
                continue;
            }
            Net net = getOrCreateNet(VerilogUtils.getNetBusSuffixName(verilogConnection.net), nets);
            if (net != null) {
                String pinName = gate.isPrimitive() ? VerilogUtils.getPrimitiveGatePinName(index)
                        : SubstitutionUtils.getContactSubstitutionName(verilogConnection.name, substitutionRule, msg);

                Node node = pinName == null ? orderedContacts.get(index) : circuit.getNodeByReference(component, pinName);
                if (node instanceof FunctionContact) {
                    FunctionContact contact = (FunctionContact) node;
                    if (contact.isInput()) {
                        net.sinks.add(contact);
                    } else {
                        net.source = contact;
                    }
                }
            }
        }
        return component;
    }

    private List<FunctionContact> getGateOrderedContacts(FunctionComponent component) {
        List<FunctionContact> result = new ArrayList<>();
        FunctionContact gateOutput = component.getGateOutput();
        if (gateOutput != null) {
            result.add(gateOutput);

            BooleanFormula setFunction = gateOutput.getSetFunction();
            for (BooleanVariable variable : FormulaUtils.extractOrderedVariables(setFunction)) {
                if (variable instanceof FunctionContact) {
                    result.add((FunctionContact) variable);
                }
            }
        }
        return result;
    }

    private FunctionComponent createModuleInstance(Circuit circuit, VerilogInstance verilogInstance,
            HashMap<String, Net> nets, Collection<VerilogModule> verilogModules) {

        final FunctionComponent component = new FunctionComponent();
        VerilogModule verilogModule = getVerilogModule(verilogModules, verilogInstance.moduleName);
        if (verilogModule == null) {
            component.setIsEnvironment(true);
        } else if (moduleFileNames != null) {
            String path = moduleFileNames.get(verilogModule);
            FileReference refinement = new FileReference();
            refinement.setPath(path);
            component.setRefinement(refinement);
        }
        component.setModule(verilogInstance.moduleName);
        circuit.add(component);
        try {
            circuit.setName(component, verilogInstance.name);
        } catch (ArgumentException e) {
            String componentRef = circuit.getNodeReference(component);
            LogUtils.logWarning("Cannot set name '" + verilogInstance.name + "' for component '" + componentRef + "'.");
        }
        HashMap<String, VerilogPort> instancePorts = getModulePortMap(verilogModule);
        for (VerilogConnection verilogConnection : verilogInstance.connections) {
            if (verilogConnection == null) {
                continue;
            }
            VerilogPort verilogPort = instancePorts.get(verilogConnection.name);
            FunctionContact contact = new FunctionContact();
            Net net = getOrCreateNet(VerilogUtils.getNetBusSuffixName(verilogConnection.net), nets);
            if (verilogPort == null) {
                net.undefined.add(contact);
            } else if (verilogPort.isOutput()) {
                contact.setIOType(IOType.OUTPUT);
                net.source = contact;
            } else {
                contact.setIOType(IOType.INPUT);
                net.sinks.add(contact);
            }
            component.add(contact);
            if (verilogConnection.name != null) {
                circuit.setName(contact, verilogConnection.name);
            }
        }
        return component;
    }

    private VerilogModule getVerilogModule(Collection<VerilogModule> verilogModules, String moduleName) {
        if ((moduleName != null) && (verilogModules != null)) {
            for (VerilogModule verilogModule : verilogModules) {
                if (moduleName.equals(verilogModule.name)) {
                    return verilogModule;
                }
            }
        }
        return null;
    }

    private void insertMutexes(Collection<Mutex> mutexes, Circuit circuit, HashMap<String, Net> nets) {
        LinkedList<String> internalSignals = new LinkedList<>();
        for (Mutex instanceMutex : mutexes == null ? new ArrayList<Mutex>() : mutexes) {
            if (instanceMutex.g1.type == Signal.Type.INTERNAL) {
                internalSignals.add(instanceMutex.g1.name);
            }
            if (instanceMutex.g2.type == Signal.Type.INTERNAL) {
                internalSignals.add(instanceMutex.g2.name);
            }
            createMutex(circuit, instanceMutex, nets);
            removeTemporaryOutput(circuit, nets, instanceMutex.r1);
            removeTemporaryOutput(circuit, nets, instanceMutex.r2);
        }
        if (!internalSignals.isEmpty()) {
            DialogUtils.showWarning("Mutex grants will be exposed as output ports: "
                    + String.join(", ", internalSignals) + ".\n\n"
                    + "This is necessary (due to technical reasons) for verification\n"
                    + "of a circuit with mutex against its environment STG.");
        }
    }

    private void removeTemporaryOutput(Circuit circuit, HashMap<String, Net> nets, Signal signal) {
        if (signal.type == Signal.Type.INTERNAL) {
            Node node = circuit.getNodeByReference(signal.name);
            if (node instanceof FunctionContact) {
                FunctionContact contact = (FunctionContact) node;
                if (contact.isPort() && contact.isOutput()) {
                    LogUtils.logInfo("Signal '" + signal.name + "' is restored as internal.");
                    circuit.remove(contact);
                    Net net = nets.get(signal.name);
                    if (net != null) {
                        net.sinks.remove(contact);
                        if ((net.source != null) && (net.source.getParent() instanceof FunctionComponent)) {
                            FunctionComponent component = (FunctionComponent) net.source.getParent();
                            reparentAndRenameComponent(circuit, component, signal.name);
                        }
                    }
                }
            }
        }
    }

    private void reparentAndRenameComponent(Circuit circuit, FunctionComponent component, String ref) {
        String containerRef = NamespaceHelper.getParentReference(ref);
        if (!containerRef.isEmpty()) {
            PageNode container;
            Node parent = circuit.getNodeByReference(containerRef);
            if (parent instanceof PageNode) {
                container = (PageNode) parent;
            } else {
                container = circuit.createNodeWithHierarchy(containerRef, circuit.getRoot(), PageNode.class);
            }
            if (container != component.getParent()) {
                circuit.reparent(container, circuit, circuit.getRoot(), Collections.singletonList(component));
            }
        }
        HierarchyReferenceManager refManager = circuit.getReferenceManager();
        NamespaceProvider namespaceProvider = refManager.getNamespaceProvider(component);
        NameManager nameManager = refManager.getNameManager(namespaceProvider);
        String name = NamespaceHelper.getReferenceName(ref);
        String derivedName = nameManager.getDerivedName(component, name);
        circuit.setName(component, derivedName);
    }

    private FunctionComponent createWait(Circuit circuit, Wait instance, HashMap<String, Net> nets) {
        if (instance == null) {
            return null;
        }
        Wait.Type type = instance.getType();
        Wait module = CircuitSettings.parseWaitData(type);
        if ((module == null) || (module.name == null)) {
            return null;
        }
        FunctionComponent component = new FunctionComponent();
        circuit.add(component);
        component.setModule(module.name);
        component.setIsArbitrationPrimitive(true);

        reparentAndRenameComponent(circuit, component, instance.name);
        FunctionContact sigContact = addComponentPin(circuit, component, module.sig, instance.sig, nets);
        FunctionContact ctrlContact = addComponentPin(circuit, component, module.ctrl, instance.ctrl, nets);
        FunctionContact sanContact = addComponentPin(circuit, component, module.san, instance.san, nets);

        ArbitrationUtils.assignWaitFunctions(type, sigContact, ctrlContact, sanContact);
        return component;
    }

    private FunctionComponent createMutex(Circuit circuit, Mutex instance, HashMap<String, Net> nets) {
        if (instance == null) {
            return null;
        }
        Mutex module = CircuitSettings.parseMutexData();
        if ((module == null) || (module.name == null)) {
            return null;
        }
        Mutex.Protocol protocol = instance.getProtocol();
        String moduleName = ArbitrationUtils.appendMutexProtocolSuffix(module.name, protocol);
        FunctionComponent component = new FunctionComponent();
        circuit.add(component);
        component.setModule(moduleName);
        component.setIsArbitrationPrimitive(true);

        reparentAndRenameComponent(circuit, component, instance.name);
        FunctionContact r1Contact = addComponentPin(circuit, component, module.r1, instance.r1, nets);
        FunctionContact g1Contact = addComponentPin(circuit, component, module.g1, instance.g1, nets);
        FunctionContact r2Contact = addComponentPin(circuit, component, module.r2, instance.r2, nets);
        FunctionContact g2Contact = addComponentPin(circuit, component, module.g2, instance.g2, nets);

        ArbitrationUtils.assignMutexFunctions(protocol, r1Contact, g1Contact, r2Contact, g2Contact);

        setMutexGrant(circuit, instance.g1, nets);
        setMutexGrant(circuit, instance.g2, nets);
        return component;
    }

    private void setMutexGrant(Circuit circuit, Signal signal, HashMap<String, Net> nets) {
        Node node = signal.name == null ? null : circuit.getNodeByReference(signal.name);
        if (node instanceof FunctionContact) {
            FunctionContact port = (FunctionContact) node;
            switch (signal.type) {
            case INPUT:
                port.setIOType(IOType.INPUT);
                break;
            case INTERNAL:
            case OUTPUT:
                port.setIOType(IOType.OUTPUT);
                break;
            }
            Net net = getOrCreateNet(signal.name, nets);
            net.sinks.add(port);
        }
    }

    private FunctionContact addComponentPin(Circuit circuit, FunctionComponent component, Signal port,
            Signal signal, HashMap<String, Net> nets) {

        FunctionContact contact = new FunctionContact();
        if (port.type == Signal.Type.INPUT) {
            contact.setIOType(IOType.INPUT);
        } else {
            contact.setIOType(IOType.OUTPUT);
        }
        component.add(contact);
        circuit.setName(contact, port.name);
        Net net = getOrCreateNet(signal.name, nets);
        if (net != null) {
            if (port.type == Signal.Type.INPUT) {
                net.sinks.add(contact);
            } else {
                net.source = contact;
            }
        }
        return contact;
    }

    private Net getOrCreateNet(String name, HashMap<String, Net> nets) {
        if (name == null) {
            return null;
        }
        Net net = nets.get(name);
        if (net == null) {
            net = new Net();
            nets.put(name, net);
        }
        return net;
    }

    private void createConnections(Circuit circuit, Map<String, Net> nets) {
        boolean finalised = false;
        while (!finalised) {
            finalised = true;
            for (Net net : nets.values()) {
                finalised &= finaliseNet(circuit, net);
            }
        }
        for (Net net : nets.values()) {
            createConnection(circuit, net);
        }
    }

    private boolean finaliseNet(Circuit circuit, Net net) {
        boolean result = true;
        if (net.source == null) {
            if (net.undefined.size() == 1) {
                net.source = net.undefined.iterator().next();
                if (net.source.isPort()) {
                    net.source.setIOType(IOType.INPUT);
                } else {
                    net.source.setIOType(IOType.OUTPUT);
                }
                String contactRef = circuit.getNodeReference(net.source);
                LogUtils.logInfo("Source contact detected: " + contactRef);
                net.undefined.clear();
                result = false;
            } else {
                FunctionContact sourceContact = guessNetSource(circuit, net, new String[]{"O", "ON", "Y", "Z", "o"});
                if (sourceContact != null) {
                    sourceContact.setIOType(IOType.OUTPUT);
                    net.source = sourceContact;
                    net.undefined.remove(sourceContact);
                    for (FunctionContact contact : new ArrayList<>(net.undefined)) {
                        if (contact.isPin()) {
                            contact.setIOType(IOType.INPUT);
                            net.sinks.add(contact);
                            net.undefined.remove(contact);
                        }
                    }
                }
            }
        } else if (!net.undefined.isEmpty()) {
            net.sinks.addAll(net.undefined);
            for (FunctionContact contact : net.undefined) {
                if (contact.isPort()) {
                    contact.setIOType(IOType.OUTPUT);
                } else {
                    contact.setIOType(IOType.INPUT);
                }
            }
            String contactRefs = ReferenceHelper.getNodesAsString(circuit, net.undefined);
            LogUtils.logInfo("Sink contacts detected: " + contactRefs);
            net.undefined.clear();
            result = false;
        }
        return result;
    }

    private FunctionContact guessNetSource(Circuit circuit, Net net, String[] orderedCandidateOutputPinNames) {
        if (orderedCandidateOutputPinNames != null) {
            for (String outputPinName : orderedCandidateOutputPinNames) {
                FunctionContact contact = guessNetSource(circuit, net, outputPinName);
                if (contact != null) {
                    return contact;
                }
            }
        }
        return null;
    }

    private FunctionContact guessNetSource(Circuit circuit, Net net, String outputPinName) {
        if (outputPinName != null) {
            for (FunctionContact contact : net.undefined) {
                if (contact.isPin()) {
                    String contactName = circuit.getName(contact);
                    if (outputPinName.equals(contactName)) {
                        return contact;
                    }
                }
            }
        }
        return null;
    }

    private void createConnection(Circuit circuit, Net net) {
        Contact sourceContact = net.source;
        String title = circuit.getTitle();
        String prefix = "In the imported module " + (title.isEmpty() ? "" : "'" + title + "' ");
        if (sourceContact == null) {
            HashSet<FunctionContact> contacts = new HashSet<>();
            contacts.addAll(net.sinks);
            contacts.addAll(net.undefined);
            if (!contacts.isEmpty()) {
                Set<String> contactRefs = ReferenceHelper.getReferenceSet(circuit, contacts);
                LogUtils.logError(TextUtils.wrapMessageWithItems(prefix
                        + "net without a source is connected to contact", contactRefs));
            }
        } else {
            String sourceRef = circuit.getNodeReference(sourceContact);
            if (!net.undefined.isEmpty()) {
                Set<String> contactRefs = ReferenceHelper.getReferenceSet(circuit, net.undefined);
                LogUtils.logError(TextUtils.wrapMessageWithItems(prefix
                        + "net from contact '" + sourceRef + "' has undefined sink", contactRefs));
            }
            if (sourceContact.isPort() && sourceContact.isOutput()) {
                sourceContact.setIOType(IOType.INPUT);
                LogUtils.logWarning(prefix + "source contact '" + sourceRef + "' is changed to input port");
            }
            if (!sourceContact.isPort() && sourceContact.isInput()) {
                sourceContact.setIOType(IOType.OUTPUT);
                LogUtils.logWarning(prefix + "source contact '" + sourceRef + "' is changed to output pin");
            }
            for (FunctionContact sinkContact: net.sinks) {
                if (sinkContact.isPort() && sinkContact.isInput()) {
                    sinkContact.setIOType(IOType.OUTPUT);
                    LogUtils.logWarning(prefix + "sink contact '" + circuit.getNodeReference(sinkContact) + "' is changed to output port");
                }
                if (!sinkContact.isPort() && sinkContact.isOutput()) {
                    sinkContact.setIOType(IOType.INPUT);
                    LogUtils.logWarning(prefix + "sink contact '" + circuit.getNodeReference(sinkContact) + "' is changed to input pin");
                }
                try {
                    circuit.connect(sourceContact, sinkContact);
                } catch (InvalidConnectionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void setZeroDelayAttribute(HashMap<VerilogInstance, FunctionComponent> instanceComponentMap) {
        for (VerilogInstance verilogInstance: instanceComponentMap.keySet()) {
            FunctionComponent component = instanceComponentMap.get(verilogInstance);
            if ((component != null) && (verilogInstance.zeroDelay)) {
                try {
                    component.setIsZeroDelay(true);
                } catch (ArgumentException e) {
                    LogUtils.logWarning(e.getMessage()
                            + " Zero delay attribute is ignored for component '" + verilogInstance.name + "'.");
                }
            }
        }
    }

    private void setInitialState(Map<String, Net> nets, Map<String, Boolean> signalStates) {
        // Set all signals first to 1 and then to 0, to make sure they switch and trigger the neighbours
        for (String signalName : nets.keySet()) {
            Net net = nets.get(signalName);
            if (net.source != null) {
                net.source.setInitToOne(true);
            }
        }
        for (String signalName : nets.keySet()) {
            Net net = nets.get(signalName);
            if (net.source != null) {
                net.source.setInitToOne(false);
            }
        }
        // Set all signals specified as high to 1
        for (String signalName : nets.keySet()) {
            Net net = nets.get(signalName);
            if ((net.source != null) && signalStates.containsKey(signalName)) {
                boolean signalState = signalStates.get(signalName);
                net.source.setInitToOne(signalState);
            }
        }
    }

    private HashMap<String, VerilogPort> getModulePortMap(VerilogModule verilogModule) {
        HashMap<String, VerilogPort> result = new HashMap<>();
        if (verilogModule != null) {
            for (VerilogPort verilogPort : verilogModule.ports) {
                result.put(verilogPort.name, verilogPort);
            }
        }
        return result;
    }

}
