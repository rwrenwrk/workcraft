/*
*
* Copyright 2008,2009 Newcastle University
*
* This file is part of Workcraft.
*
* Workcraft is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Workcraft is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Workcraft.  If not, see <http://www.gnu.org/licenses/>.
*
*/

package org.workcraft.dom;


import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

import org.workcraft.annotations.DisplayName;
import org.workcraft.annotations.ShortName;
import org.workcraft.dom.hierarchy.NamespaceProvider;
import org.workcraft.dom.references.DefaultReferenceManager;
import org.workcraft.dom.references.HierarchicalUniqueNameReferenceManager;
import org.workcraft.dom.references.ReferenceManager;
import org.workcraft.dom.visual.VisualModel;
import org.workcraft.gui.propertyeditor.ModelProperties;
import org.workcraft.gui.propertyeditor.NamePropertyDescriptor;

/**
 * A base class for all interpreted graph models.
 */
public abstract class AbstractModel implements Model {
	private Container root;
	private ReferenceManager mgr;
	private String title = "Untitled";
	final private NodeContextTracker nodeContextTracker = new NodeContextTracker();

	public AbstractModel(Container root) {
		this(root, null);
	}

	public AbstractModel(Container root, ReferenceManager man) {
		this.root = root;
		if (man != null) {
			this.mgr = man;
		} else {
			if (root instanceof NamespaceProvider) {
				this.mgr = new HierarchicalUniqueNameReferenceManager();
			} else {
				this.mgr = new DefaultReferenceManager();
			}
		}
		this.nodeContextTracker.attach(root);
		this.mgr.attach(root);
	}

	public Model getMathModel() {
		return this;
	}

	public VisualModel getVisualModel() {
		return null;
	}

	public void add (Node node) {
		root.add(node);
	}

	public void remove (Node node) {
		if (node.getParent() instanceof Container)
			((Container)node.getParent()).remove(node);
		else
			throw new RuntimeException ("Cannot remove a child node from a node that is not a Container (or null).");
	}

	public void remove (Collection<Node> nodes) {
		LinkedList<Node> toRemove = new LinkedList<Node>(nodes);
		for (Node node : toRemove) {
			// some nodes may be removed as a result of removing other nodes in the list,
			// e.g. hanging connections so need to check
			if (node.getParent() != null)
				remove (node);
		}
	}

	public String getDisplayName() {
		DisplayName name = this.getClass().getAnnotation(DisplayName.class);
		if (name == null) {
			return this.getClass().getSimpleName();
		} else {
			return name.value();
		}
	}

	public String getShortName() {
		ShortName name = this.getClass().getAnnotation(ShortName.class);
		if (name != null) {
			return name.value();
		} else {
			String result = "";
			String s = getDisplayName();
			boolean b = true;
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (b && !Character.isSpaceChar(c) || Character.isUpperCase(c)) {
					result += c;
				}
				b = Character.isSpaceChar(c);
			}
			return result;
		}
	}

	final public String getTitle() {
		return title;
	}

	final public void setTitle(String title) {
		this.title = title;
	}

	public final Container getRoot() {
		return root;
	}

	public Set<Connection> getConnections(Node component) {
		return nodeContextTracker.getConnections(component);
	}

	public Set<Node> getPostset(Node component) {
		return nodeContextTracker.getPostset(component);
	}

	public Set<Node> getPreset(Node component) {
		return nodeContextTracker.getPreset(component);
	}

	@Override
	public Node getNodeByReference(String reference) {
		return getNodeByReference(null, reference);
	}

	@Override
	public String getNodeReference(Node node) {
		return getNodeReference(null, node);
	}

	@Override
	public Node getNodeByReference(NamespaceProvider provider, String reference) {
		return mgr.getNodeByReference(provider, reference);
	}

	@Override
	public String getNodeReference(NamespaceProvider provider, Node node) {
		return mgr.getNodeReference(provider, node);
	}

	@Override
	public ModelProperties getProperties(Node node) {
		ModelProperties properties = new ModelProperties();
		if ((node != null) && !(node instanceof Connection)) {
			properties.add(new NamePropertyDescriptor(this, node));
		}
		return properties;
	}

	public ReferenceManager getReferenceManager() {
		return mgr;
	}

	public String getName(Node node) {

		if (mgr instanceof HierarchicalUniqueNameReferenceManager)
			return ((HierarchicalUniqueNameReferenceManager)mgr).getName(node);

		return mgr.getNodeReference(null, node);
	}

	public void setName(Node node, String name) {
		if (mgr instanceof HierarchicalUniqueNameReferenceManager) {
			((HierarchicalUniqueNameReferenceManager)mgr).setName(node, name);
		}
	}

	public void reparent(Container targetContainer, Model sourceModel, Collection<Node> sourceNodes) {
		// reparent for the general model undefined?
	}

	public Connection getConnection(Node first, Node second) {
		for(Connection connection : getConnections(first)) {
			if (connection.getSecond() == second) return connection;
		}
		return null;
	}

}