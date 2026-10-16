package fr.openent.caliper.security;

import io.vertx.core.json.JsonArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Les scopes OAuth du service.
 *
 * <p><strong>Ils ne sont pas normalisés.</strong> Caliper décrit un endpoint protégé par un
 * jeton porteur convenu hors norme, et ne définit aucun scope. Ceux-ci sont donc les nôtres, et
 * portent un nom simple plutôt qu'une IRI sous {@code purl.imsglobal.org} : s'approprier
 * l'espace de noms de 1EdTech pour des scopes qu'il ne définit pas induirait en erreur le
 * premier client qui irait y chercher leur définition.</p>
 */
public final class Scopes {

	public static final String EVENT_CREATE = "caliper.event.create";
	public static final String EVENT_READONLY = "caliper.event.readonly";

	public static final List<String> ALL = Collections.unmodifiableList(
			Arrays.asList(EVENT_CREATE, EVENT_READONLY));

	private Scopes() {
	}

	public static List<String> split(String scope) {
		final List<String> scopes = new ArrayList<String>();
		if (scope != null) {
			for (String s : scope.trim().split("\\s+")) {
				if (!s.isEmpty() && !scopes.contains(s)) {
					scopes.add(s);
				}
			}
		}
		return scopes;
	}

	public static List<String> grant(String requested, JsonArray allowed) {
		final List<String> granted = new ArrayList<String>();
		final List<String> wanted = split(requested);
		if (allowed == null) {
			return granted;
		}
		if (wanted.isEmpty()) {
			for (Object o : allowed) {
				if (o instanceof String && ALL.contains(o) && !granted.contains(o)) {
					granted.add((String) o);
				}
			}
			return granted;
		}
		for (String s : wanted) {
			if (allowed.contains(s) && !granted.contains(s)) {
				granted.add(s);
			}
		}
		return granted;
	}
}
