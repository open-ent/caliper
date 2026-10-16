package fr.openent.caliper.model;

import fr.openent.caliper.core.Uris;
import fr.openent.caliper.core.Vocabulary;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.UUID;

/**
 * Construction d'événements et d'enveloppes Caliper 1.2.
 *
 * <p>Classe <strong>pure</strong> : aucune base, aucun Vert.x, aucun réseau.</p>
 *
 * <p>Deux règles de la norme que cette classe tient pour l'appelant. <strong>Chaque événement
 * porte son propre {@code @context}</strong>, même à l'intérieur d'une enveloppe qui déclare
 * déjà sa version — la norme l'exige explicitement, chaque élément du tableau {@code data}
 * devant être un document JSON-LD autonome. Et <strong>une enveloppe n'admet aucune propriété
 * supplémentaire</strong> : ses quatre propriétés, pas une de plus.</p>
 */
public final class Events {

	private final Uris uris;

	public Events(Uris uris) {
		this.uris = uris;
	}

	// ------------------------------------------------------------------ entités

	public JsonObject person(String userId) {
		return new JsonObject()
				.put("id", uris.person(userId))
				.put("type", Vocabulary.PERSON);
	}

	public JsonObject application(String id, String name) {
		final JsonObject application = new JsonObject()
				.put("id", id)
				.put("type", Vocabulary.SOFTWARE_APPLICATION);
		if (name != null && !name.isEmpty()) {
			application.put("name", name);
		}
		return application;
	}

	/** L'ENT lui-même, présent sur chaque événement comme application émettrice. */
	public JsonObject edApp() {
		return application(uris.edApp(), null);
	}

	// ------------------------------------------------------------------ événements

	/**
	 * Un événement.
	 *
	 * @throws IllegalArgumentException si l'action n'est pas admise pour ce type. Un couple
	 *         invalide produit un événement que les magasins rejettent — ou, pire, comptent de
	 *         travers : mieux vaut échouer ici.
	 */
	public JsonObject event(String type, String action, JsonObject actor, JsonObject object,
							String eventTime) {
		if (!Vocabulary.isKnownEvent(type)) {
			throw new IllegalArgumentException("type d'événement inconnu : " + type);
		}
		if (!Vocabulary.allows(type, action)) {
			throw new IllegalArgumentException("action « " + action + " » inadmissible pour un "
					+ type + " ; attendu : " + Vocabulary.actionsOf(type));
		}
		return new JsonObject()
				.put("@context", Vocabulary.CONTEXT)
				.put("id", "urn:uuid:" + UUID.randomUUID())
				.put("type", type)
				.put("actor", actor)
				.put("action", action)
				.put("object", object)
				.put("eventTime", eventTime)
				.put("edApp", edApp());
	}

	/** Une personne a utilisé un module de l'ENT. */
	public JsonObject toolUse(String userId, String module, String eventTime) {
		return event(Vocabulary.TOOL_USE_EVENT, Vocabulary.USED, person(userId),
				application(uris.module(module), module), eventTime);
	}

	/** Une personne s'est connectée. L'objet d'un événement de session est l'application. */
	public JsonObject login(String userId, String eventTime) {
		return event(Vocabulary.SESSION_EVENT, Vocabulary.LOGGED_IN, person(userId), edApp(),
				eventTime);
	}

	/** Ajoute des informations hors norme sous {@code extensions}, seul endroit admis. */
	public static JsonObject withExtensions(JsonObject event, JsonObject extensions) {
		if (extensions != null && !extensions.isEmpty()) {
			event.put("extensions", extensions);
		}
		return event;
	}

	// ------------------------------------------------------------------ enveloppe

	/**
	 * L'enveloppe qui transporte les événements.
	 *
	 * <p>{@code dataVersion} vaut l'IRI de contexte, et rien d'autre : c'est par là qu'un
	 * endpoint reconnaît la version qu'on lui parle.</p>
	 */
	public JsonObject envelope(String sendTime, List<JsonObject> events) {
		final JsonArray data = new JsonArray();
		for (JsonObject event : events) {
			data.add(event);
		}
		return new JsonObject()
				.put("sensor", uris.sensor())
				.put("sendTime", sendTime)
				.put("dataVersion", Vocabulary.CONTEXT)
				.put("data", data);
	}
}
