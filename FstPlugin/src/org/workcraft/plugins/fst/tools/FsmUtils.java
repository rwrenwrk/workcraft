package org.workcraft.plugins.fst.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import org.workcraft.dom.Node;
import org.workcraft.plugins.fsm.Event;
import org.workcraft.plugins.fsm.Fsm;
import org.workcraft.plugins.fsm.State;

public class FsmUtils {

	static public HashMap<State, HashSet<Event>> calcStateEventsMap(final Fsm fsm) {
		HashMap<State, HashSet<Event>> stateEvents = new HashMap<State, HashSet<Event>>();
		for (State state: fsm.getStates()) {
			HashSet<Event> events = new HashSet<Event>();
			stateEvents.put(state, events);
		}
		for (Event event: fsm.getEvents()) {
			State state = (State)event.getFirst();
			HashSet<Event> events = stateEvents.get(state);
			events.add(event);
		}
		return stateEvents;
	}

	static public String statesToString(final Fsm fsm, Collection<State> states) {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		ArrayList<String> refs = getReferenceList(fsm, (Collection)states);
		Collections.sort(refs);
		return refsToString(refs);
	}

	static private ArrayList<String> getReferenceList(final Fsm fsm, Collection<Node> nodes) {
		ArrayList<String> refs = new ArrayList<String>();
		for (Node node: nodes) {
			String ref = fsm.getNodeReference(node);
			if (ref != null) {
				refs.add(ref);
			}
		}
		return refs;
	}

	static private String refsToString(ArrayList<String> refs) {
		String str = "";
		for (String ref: refs) {
			if (!str.isEmpty()) {
				str += ", ";
			}
			str += ref;
		}
		return wrapString(str, 50);
	}

	static private String wrapString(String str, int len) {
		StringBuilder sb = new StringBuilder(str);
		int i = 0;
		while ((i + len < sb.length()) && ((i = sb.lastIndexOf(" ", i + len)) != -1)) {
		    sb.replace(i, i + 1, "\n");
		}
		return sb.toString();
	}

}
