package org.workcraft.plugins.circuit.utils;

import org.workcraft.dom.Container;
import org.workcraft.dom.hierarchy.NamespaceHelper;
import org.workcraft.dom.math.MathNode;
import org.workcraft.dom.references.Identifier;
import org.workcraft.dom.visual.ConnectionHelper;
import org.workcraft.dom.visual.VisualComponent;
import org.workcraft.dom.visual.VisualPage;
import org.workcraft.dom.visual.connections.VisualConnection;
import org.workcraft.exceptions.InvalidConnectionException;
import org.workcraft.plugins.circuit.*;
import org.workcraft.types.Pair;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class SquashUtils {

    public static void squashComponent(VisualCircuit circuit, VisualFunctionComponent component, VisualCircuit componentModel) {
        String pageName = circuit.getMathName(component);
        circuit.setMathName(component, Identifier.makeInternal(pageName));
        Container container = (Container) component.getParent();
        VisualPage page = circuit.createVisualPage(container);
        circuit.setMathName(page, pageName);
        circuit.reparent(page, componentModel, componentModel.getRoot(), null);
        page.setPosition(component.getPosition());

        for (VisualFunctionContact pin : component.getVisualFunctionContacts()) {
            String pinName = pin.getName();
            String pageRef = circuit.getMathReference(page);
            String portRef = NamespaceHelper.getReference(pageRef, pinName);
            VisualFunctionContact port = circuit.getVisualComponentByMathReference(portRef, VisualFunctionContact.class);
            mergeConnections(circuit, pin, port);
        }
        circuit.remove(component);
    }

    public static void inheritInitialState(VisualCircuit circuit, VisualFunctionComponent component,
            VisualCircuit componentModel) {

        inheritInitialState(circuit.getMathModel(), component.getReferencedComponent(), componentModel.getMathModel());
    }

    public static void inheritInitialState(Circuit circuit, FunctionComponent component, Circuit componentModel) {
        for (FunctionContact pin : component.getFunctionContacts()) {
            String pinName = pin.getName();
            MathNode node = componentModel.getNodeByReference(pinName);
            if (node instanceof Contact) {
                Contact port = (Contact) node;
                Pair<Contact, Boolean> portDriverPair = CircuitUtils.findDriverAndInversionSkipZeroDelay(componentModel, port);
                Pair<Contact, Boolean> pinDriverPair = CircuitUtils.findDriverAndInversionSkipZeroDelay(circuit, pin);
                if ((pinDriverPair != null) && (portDriverPair != null)) {
                    Contact portDriver = portDriverPair.getFirst();
                    Contact pinDriver = pinDriverPair.getFirst();
                    boolean inversion = pinDriverPair.getSecond() ^ portDriverPair.getSecond();
                    portDriver.setInitToOne(pinDriver.getInitToOne() ^ inversion);
                }
            }
        }
    }

    private static void mergeConnections(VisualCircuit circuit, VisualFunctionContact pin, VisualFunctionContact port) {
        if ((pin != null) && (port != null) && (pin.isDriver() == port.isDriven())) {
            VisualFunctionContact drivenContact = pin.isDriven() ? pin : port;
            VisualFunctionContact driverContact = port.isDriver() ? port : pin;

            Map<VisualComponent, LinkedList<Point2D>> fromShapeComponents = new HashMap<>();
            for (VisualComponent fromComponent : circuit.getPreset(drivenContact, VisualComponent.class)) {
                VisualConnection connection = circuit.getConnection(fromComponent, drivenContact);
                if (connection != null) {
                    fromShapeComponents.put(fromComponent, ConnectionHelper.getControlPoints(connection));
                }
            }
            Map<VisualComponent, LinkedList<Point2D>> toShapeComponents = new HashMap<>();
            for (VisualComponent toComponent : circuit.getPostset(driverContact, VisualComponent.class)) {
                VisualConnection connection = circuit.getConnection(driverContact, toComponent);
                if (connection != null) {
                    toShapeComponents.put(toComponent, ConnectionHelper.getControlPoints(connection));
                }
            }
            circuit.remove(pin);
            circuit.remove(port);
            mergeConnections(circuit, fromShapeComponents, toShapeComponents);
        }
    }

    private static void mergeConnections(VisualCircuit circuit, Map<VisualComponent, LinkedList<Point2D>> fromShapeComponents,
            Map<VisualComponent, LinkedList<Point2D>> toShapeComponents) {

        for (VisualComponent fromComponent : fromShapeComponents.keySet()) {
            LinkedList<Point2D> shape = new LinkedList<>(fromShapeComponents.get(fromComponent));
            for (VisualComponent toComponent : toShapeComponents.keySet()) {
                shape.addAll(toShapeComponents.get(toComponent));
                try {
                    VisualConnection connection = circuit.connect(fromComponent, toComponent);
                    ConnectionHelper.addControlPoints(connection, shape);
                } catch (InvalidConnectionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
