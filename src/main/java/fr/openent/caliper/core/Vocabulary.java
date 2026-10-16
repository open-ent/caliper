package fr.openent.caliper.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le vocabulaire de Caliper 1.2 : contexte, types d'événement, actions, et surtout la
 * correspondance entre les deux.
 *
 * <p>C'est là que se gagne ou se perd la conformité. Caliper n'admet pas n'importe quelle action
 * sur n'importe quel événement : un {@code ToolUseEvent} ne connaît que {@code Used}, un
 * {@code SessionEvent} ne connaît que {@code LoggedIn}, {@code LoggedOut} et {@code TimedOut}.
 * Un couple invalide produit un événement que les magasins d'analytique rejettent — ou, pire,
 * acceptent et comptent de travers.</p>
 */
public final class Vocabulary {

	/** L'IRI de contexte, et la valeur exacte de {@code dataVersion} dans une enveloppe. */
	public static final String CONTEXT = "http://purl.imsglobal.org/ctx/caliper/v1p2";

	public static final String EVENT = "Event";
	public static final String SESSION_EVENT = "SessionEvent";
	public static final String TOOL_USE_EVENT = "ToolUseEvent";
	public static final String NAVIGATION_EVENT = "NavigationEvent";
	public static final String VIEW_EVENT = "ViewEvent";

	public static final String PERSON = "Person";
	public static final String SOFTWARE_APPLICATION = "SoftwareApplication";
	public static final String SESSION = "Session";
	public static final String DIGITAL_RESOURCE = "DigitalResource";

	public static final String USED = "Used";
	public static final String LOGGED_IN = "LoggedIn";
	public static final String LOGGED_OUT = "LoggedOut";
	public static final String TIMED_OUT = "TimedOut";
	public static final String NAVIGATED_TO = "NavigatedTo";
	public static final String VIEWED = "Viewed";

	/** Les actions que chaque type d'événement admet. */
	private static final Map<String, Set<String>> ACTIONS = actions();

	private static Map<String, Set<String>> actions() {
		final Map<String, Set<String>> map = new HashMap<String, Set<String>>();
		map.put(TOOL_USE_EVENT, unmodifiable(USED));
		map.put(SESSION_EVENT, unmodifiable(LOGGED_IN, LOGGED_OUT, TIMED_OUT));
		map.put(NAVIGATION_EVENT, unmodifiable(NAVIGATED_TO));
		map.put(VIEW_EVENT, unmodifiable(VIEWED));
		return Collections.unmodifiableMap(map);
	}

	private Vocabulary() {
	}

	/** @return vrai si ce type d'événement est connu. Le type générique {@code Event} l'est. */
	public static boolean isKnownEvent(String type) {
		return EVENT.equals(type) || ACTIONS.containsKey(type);
	}

	/**
	 * @return vrai si l'action est admise pour ce type d'événement. Le type générique
	 *         {@code Event} accepte toute action : c'est la porte de sortie que la norme laisse
	 *         pour ce qu'aucun profil ne décrit.
	 */
	public static boolean allows(String eventType, String action) {
		if (EVENT.equals(eventType)) {
			return action != null && !action.trim().isEmpty();
		}
		final Set<String> allowed = ACTIONS.get(eventType);
		return allowed != null && allowed.contains(action);
	}

	public static Set<String> actionsOf(String eventType) {
		final Set<String> allowed = ACTIONS.get(eventType);
		return allowed == null ? Collections.<String>emptySet() : allowed;
	}

	public static List<String> eventTypes() {
		final List<String> types = new java.util.ArrayList<String>(ACTIONS.keySet());
		Collections.sort(types);
		types.add(0, EVENT);
		return types;
	}

	private static Set<String> unmodifiable(String... values) {
		return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
	}
}
