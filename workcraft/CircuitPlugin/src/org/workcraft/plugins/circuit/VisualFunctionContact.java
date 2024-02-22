package org.workcraft.plugins.circuit;

import org.workcraft.annotations.DisplayName;
import org.workcraft.annotations.Hotkey;
import org.workcraft.annotations.SVGIcon;
import org.workcraft.dom.Node;
import org.workcraft.dom.visual.BoundingBoxHelper;
import org.workcraft.dom.visual.DrawRequest;
import org.workcraft.formula.BooleanFormula;
import org.workcraft.formula.visitors.FormulaRenderingResult;
import org.workcraft.formula.visitors.FormulaToGraphics;
import org.workcraft.gui.tools.Decoration;
import org.workcraft.observation.PropertyChangedEvent;
import org.workcraft.observation.StateEvent;
import org.workcraft.observation.StateObserver;
import org.workcraft.serialisation.NoAutoSerialisation;
import org.workcraft.utils.ColorUtils;
import org.workcraft.utils.Hierarchy;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

@DisplayName("Input/output port")
@Hotkey(KeyEvent.VK_P)
@SVGIcon("images/circuit-node-port.svg")
public class VisualFunctionContact extends VisualContact implements StateObserver {

    private enum ArrowType { UP, DOWN }

    private static final double size = 0.3;
    private static final double X_FUNC_OFFSET = 1.25 * size;
    private static final double Y_FUNC_OFFSET = 0.5 * size;
    private static final FontRenderContext context = new FontRenderContext(AffineTransform.getScaleInstance(1000.0, 1000.0), true, true);
    private static Font functionFont;

    private FormulaRenderingResult renderedSetFunction = null;
    private FormulaRenderingResult renderedResetFunction = null;
    private static double functionFontSize = CircuitSettings.getContactFontSize();

    static {
        try {
            functionFont = Font.createFont(Font.TYPE1_FONT, ClassLoader.getSystemResourceAsStream("fonts/eurm10.pfb"));
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
        }
    }

    public VisualFunctionContact(FunctionContact contact) {
        super(contact);
    }

    @Override
    public FunctionContact getReferencedComponent() {
        return (FunctionContact) super.getReferencedComponent();
    }

    @NoAutoSerialisation
    public BooleanFormula getSetFunction() {
        return getReferencedComponent().getSetFunction();
    }

    @NoAutoSerialisation
    public void setSetFunction(BooleanFormula setFunction) {
        if (getParent() instanceof VisualFunctionComponent) {
            VisualFunctionComponent p = (VisualFunctionComponent) getParent();
            p.invalidateRenderingResult();
        }
        renderedSetFunction = null;
        getReferencedComponent().setSetFunction(setFunction);
    }

    @NoAutoSerialisation
    public BooleanFormula getResetFunction() {
        return getReferencedComponent().getResetFunction();
    }

    @NoAutoSerialisation
    public void setResetFunction(BooleanFormula resetFunction) {
        if (getParent() instanceof VisualFunctionComponent) {
            VisualFunctionComponent p = (VisualFunctionComponent) getParent();
            p.invalidateRenderingResult();
        }
        renderedResetFunction = null;
        getReferencedComponent().setResetFunction(resetFunction);
    }

    public void invalidateRenderedFormula() {
        renderedSetFunction = null;
        renderedResetFunction = null;
    }

    private Font getFunctionFont() {
        return functionFont.deriveFont((float) CircuitSettings.getContactFontSize());
    }

    private FormulaRenderingResult getRenderedSetFunction() {
        if (Math.abs(CircuitSettings.getContactFontSize() - functionFontSize) > 0.001) {
            functionFontSize = CircuitSettings.getContactFontSize();
            renderedSetFunction = null;
        }
        BooleanFormula setFunction = getReferencedComponent().getSetFunction();
        if (setFunction == null) {
            renderedSetFunction = null;
        } else if (renderedSetFunction == null) {
            renderedSetFunction = FormulaToGraphics.render(setFunction, context, getFunctionFont());
        }
        return renderedSetFunction;
    }

    private Point2D getSetFormulaOffset() {
        double xOffset = X_FUNC_OFFSET;
        double yOffset = -1.25 * Y_FUNC_OFFSET;
        FormulaRenderingResult renderingResult = getRenderedSetFunction();
        if (renderingResult != null) {
            Direction dir = getDirection();
            if (!(getParent() instanceof VisualFunctionComponent)) {
                dir = dir.flip();
            }
            if ((dir == Direction.SOUTH) || (dir == Direction.WEST)) {
                xOffset = -(X_FUNC_OFFSET + renderingResult.boundingBox.getWidth());
            }
        }
        return new Point2D.Double(xOffset, yOffset);
    }

    private Rectangle2D getSetBoundingBox() {
        Rectangle2D bb = null;
        FormulaRenderingResult setRenderingResult = getRenderedSetFunction();
        if (setRenderingResult != null) {
            bb = BoundingBoxHelper.move(setRenderingResult.boundingBox, getSetFormulaOffset());
            Direction dir = getDirection();
            if (!(getParent() instanceof VisualFunctionComponent)) {
                dir = dir.flip();
            }
            if ((dir == Direction.NORTH) || (dir == Direction.SOUTH)) {
                AffineTransform rotateTransform = new AffineTransform();
                rotateTransform.quadrantRotate(-1);
                bb = BoundingBoxHelper.transform(bb, rotateTransform);
            }
        }
        return bb;
    }

    private FormulaRenderingResult getRenderedResetFunction() {
        if (Math.abs(CircuitSettings.getContactFontSize() - functionFontSize) > 0.001) {
            functionFontSize = CircuitSettings.getContactFontSize();
            renderedResetFunction = null;
        }
        BooleanFormula resetFunction = getReferencedComponent().getResetFunction();
        if (resetFunction == null) {
            renderedResetFunction = null;
        } else if (renderedResetFunction == null) {
            renderedResetFunction = FormulaToGraphics.render(resetFunction, context, getFunctionFont());
        }
        return renderedResetFunction;
    }

    private Point2D getResetFormulaOffset() {
        double xOffset = X_FUNC_OFFSET;
        double yOffset = Y_FUNC_OFFSET;
        FormulaRenderingResult renderingResult = getRenderedResetFunction();
        if (renderingResult != null) {
            Direction dir = getDirection();
            if (!(getParent() instanceof VisualFunctionComponent)) {
                dir = dir.flip();
            }
            if ((dir == Direction.SOUTH) || (dir == Direction.WEST)) {
                xOffset = -(X_FUNC_OFFSET + renderingResult.boundingBox.getWidth());
            }
            yOffset = Y_FUNC_OFFSET + renderingResult.boundingBox.getHeight();
        }
        return new Point2D.Double(xOffset, yOffset);
    }

    private Rectangle2D getResetBoundingBox() {
        Rectangle2D bb = null;
        FormulaRenderingResult renderingResult = getRenderedResetFunction();
        if (renderingResult != null) {
            bb = BoundingBoxHelper.move(renderingResult.boundingBox, getResetFormulaOffset());
            Direction dir = getDirection();
            if (!(getParent() instanceof VisualFunctionComponent)) {
                dir = dir.flip();
            }
            if ((dir == Direction.NORTH) || (dir == Direction.SOUTH)) {
                AffineTransform rotateTransform = new AffineTransform();
                rotateTransform.quadrantRotate(-1);
                bb = BoundingBoxHelper.transform(bb, rotateTransform);
            }
        }
        return bb;
    }

    private void drawArrow(Graphics2D g, ArrowType arrowType, double arrX, double arrY) {
        double s = CircuitSettings.getContactFontSize();
        g.setStroke(new BasicStroke((float) s / 25));
        double s1 = 0.75 * s;
        double s2 = 0.45 * s;
        double s3 = 0.30 * s;
        switch (arrowType) {
        case DOWN:
            Line2D upLine = new Line2D.Double(arrX, arrY - s1, arrX, arrY - s3);
            Path2D upPath = new Path2D.Double();
            upPath.moveTo(arrX - 0.05, arrY - s3);
            upPath.lineTo(arrX + 0.05, arrY - s3);
            upPath.lineTo(arrX, arrY);
            upPath.closePath();
            g.fill(upPath);
            g.draw(upLine);
            break;
        case UP:
            Line2D downLine = new Line2D.Double(arrX, arrY, arrX, arrY - s2);
            Path2D downPath = new Path2D.Double();
            downPath.moveTo(arrX - 0.05, arrY - s2);
            downPath.lineTo(arrX + 0.05, arrY - s2);
            downPath.lineTo(arrX, arrY - s1);
            downPath.closePath();
            g.fill(downPath);
            g.draw(downLine);
            break;
        }
    }

    private void drawFormula(Graphics2D g, ArrowType arrowType, Point2D offset, FormulaRenderingResult renderingResult) {
        if (renderingResult != null) {
            Direction dir = getDirection();
            if (!(getParent() instanceof VisualFunctionComponent)) {
                dir = dir.flip();
            }

            AffineTransform savedTransform = g.getTransform();
            if ((dir == Direction.NORTH) || (dir == Direction.SOUTH)) {
                AffineTransform rotateTransform = new AffineTransform();
                rotateTransform.quadrantRotate(-1);
                g.transform(rotateTransform);
            }

            double dXArrow = -0.1;
            if ((dir == Direction.SOUTH) || (dir == Direction.WEST)) {
                dXArrow = 0.1 + renderingResult.boundingBox.getWidth();
            }

            drawArrow(g, arrowType, offset.getX() + dXArrow, offset.getY());

            g.translate(offset.getX(), offset.getY());
            renderingResult.draw(g);
            g.setTransform(savedTransform);
        }
    }

    @Override
    public void draw(DrawRequest r) {
        if (needsFormulas()) {
            Graphics2D g = r.getGraphics();
            Decoration d = r.getDecoration();
            g.setColor(ColorUtils.colorise(getForegroundColor(), d.getColorisation()));
            FormulaRenderingResult renderingResult;
            renderingResult = getRenderedSetFunction();
            if (renderingResult != null) {
                drawFormula(g, ArrowType.UP, getSetFormulaOffset(), renderingResult);
            }
            renderingResult = getRenderedResetFunction();
            if (renderingResult != null) {
                drawFormula(g, ArrowType.DOWN, getResetFormulaOffset(), renderingResult);
            }
        }
        super.draw(r);
    }

    private boolean needsFormulas() {
        boolean result = false;
        Node parent = getParent();
        if ((parent != null) && CircuitSettings.getShowContactFunctions()) {
            // Primary input port
            if (!(parent instanceof VisualCircuitComponent) && isInput()) {
                result = true;
            }
            // Output port of a BOX-rendered component
            if ((parent instanceof VisualFunctionComponent) && isOutput()) {
                VisualFunctionComponent component = (VisualFunctionComponent) parent;
                if (component.getRenderingResult() == null) {
                    result = true;
                }
            }
        }
        return result;
    }

    @Override
    public Rectangle2D getBoundingBoxInLocalSpace() {
        Rectangle2D bb = super.getBoundingBoxInLocalSpace();
        if (needsFormulas()) {
            bb = BoundingBoxHelper.union(bb, getSetBoundingBox());
            bb = BoundingBoxHelper.union(bb, getResetBoundingBox());
        }
        return bb;
    }

    private Collection<VisualFunctionContact> getAllContacts() {
        HashSet<VisualFunctionContact> result = new HashSet<>();
        Node root = Hierarchy.getRoot(this);
        if (root != null) {
            result.addAll(Hierarchy.getDescendantsOfType(root, VisualFunctionContact.class));
        }
        return result;
    }

    @Override
    public void notify(StateEvent e) {
        if (e instanceof PropertyChangedEvent) {
            PropertyChangedEvent pc = (PropertyChangedEvent) e;
            String propertyName = pc.getPropertyName();
            if (propertyName.equals(FunctionContact.PROPERTY_FUNCTION)) {
                invalidateRenderedFormula();
            }
            if (propertyName.equals(Contact.PROPERTY_NAME)) {
                for (VisualFunctionContact vc : getAllContacts()) {
                    vc.invalidateRenderedFormula();
                }
            }
        }
        super.notify(e);
    }

}
