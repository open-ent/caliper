package fr.openent.caliper.model;

import fr.openent.caliper.core.CaliperException;
import fr.openent.caliper.core.Vocabulary;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vérification d'une enveloppe déposée par un capteur.
 *
 * <p>Classe <strong>pure</strong>, et volontairement <strong>sévère</strong> : une enveloppe
 * dont un seul événement est fautif est refusée en entier.</p>
 *
 * <p>Ce n'est pas de la rigidité. Un magasin d'analytique qui accepte une enveloppe en écartant
 * silencieusement deux événements produit des comptes faux, que personne ne remarquera jamais —
 * alors qu'une enveloppe refusée, avec la raison et le rang de l'événement fautif, est renvoyée
 * par le capteur après correction. Entre perdre des données en silence et refuser bruyamment,
 * une plateforme d'analytique doit refuser.</p>
 */
public final class Envelope {

	/** La norme interdit toute propriété supplémentaire dans une enveloppe. */
	private static final Set<String> ENVELOPE_PROPERTIES = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList("sensor", "sendTime", "dataVersion", "data")));

	/** Les propriétés qu'un événement doit porter. */
	private static final List<String> REQUIRED_EVENT_PROPERTIES =
			Collections.unmodifiableList(Arrays.asList(
					"@context", "id", "type", "actor", "action", "object", "eventTime"));

	private final String sensor;
	private final String sendTime;
	private final List<JsonObject> events;

	private Envelope(String sensor, String sendTime, List<JsonObject> events) {
		this.sensor = sensor;
		this.sendTime = sendTime;
		this.events = events;
	}

	public String sensor() {
		return sensor;
	}

	public String sendTime() {
		return sendTime;
	}

	public List<JsonObject> events() {
		return events;
	}

	public static Envelope parse(JsonObject body) throws CaliperException {
		if (body == null) {
			throw new CaliperException("envelope.empty");
		}
		for (String property : ENVELOPE_PROPERTIES) {
			if (!body.containsKey(property)) {
				throw new CaliperException("envelope.missing." + property);
			}
		}
		for (String property : body.fieldNames()) {
			if (!ENVELOPE_PROPERTIES.contains(property)) {
				// « Custom properties are not permitted » : une enveloppe qui en porte vient
				// d'un capteur qui parle un autre dialecte, et le reste est douteux.
				throw new CaliperException("envelope.unexpected.property:" + property);
			}
		}
		final String dataVersion = body.getString("dataVersion");
		if (!Vocabulary.CONTEXT.equals(dataVersion)) {
			throw new CaliperException("envelope.unsupported.dataVersion:" + dataVersion);
		}
		final Object data = body.getValue("data");
		if (!(data instanceof JsonArray)) {
			throw new CaliperException("envelope.data.not.an.array");
		}

		final JsonArray array = (JsonArray) data;
		final List<JsonObject> events = new ArrayList<JsonObject>(array.size());
		for (int i = 0; i < array.size(); i++) {
			final Object item = array.getValue(i);
			if (!(item instanceof JsonObject)) {
				throw new CaliperException("envelope.data." + i + ".not.an.object");
			}
			events.add(validateEvent((JsonObject) item, i));
		}
		return new Envelope(body.getString("sensor"), body.getString("sendTime"), events);
	}

	private static JsonObject validateEvent(JsonObject event, int index) throws CaliperException {
		for (String property : REQUIRED_EVENT_PROPERTIES) {
			if (!event.containsKey(property) || event.getValue(property) == null) {
				throw new CaliperException("event." + index + ".missing." + property);
			}
		}
		if (!Vocabulary.CONTEXT.equals(event.getString("@context"))) {
			// Chaque événement doit être un document JSON-LD autonome : le contexte de
			// l'enveloppe ne vaut pas pour lui.
			throw new CaliperException("event." + index + ".unsupported.context:"
					+ event.getString("@context"));
		}
		final String type = event.getString("type");
		if (!Vocabulary.isKnownEvent(type)) {
			throw new CaliperException("event." + index + ".unknown.type:" + type);
		}
		final String action = event.getString("action");
		if (!Vocabulary.allows(type, action)) {
			throw new CaliperException("event." + index + ".action.not.allowed:" + action
					+ " (" + type + " attend " + Vocabulary.actionsOf(type) + ")");
		}
		requireIdentified(event, index, "actor");
		requireIdentified(event, index, "object");
		return event;
	}

	/** Un acteur ou un objet sans identifiant ne désigne rien et ne se recoupe avec rien. */
	private static void requireIdentified(JsonObject event, int index, String property)
			throws CaliperException {
		final Object value = event.getValue(property);
		if (value instanceof String) {
			// La norme admet qu'une entité soit citée par son seul IRI.
			return;
		}
		if (!(value instanceof JsonObject) || ((JsonObject) value).getString("id") == null) {
			throw new CaliperException("event." + index + "." + property + ".without.id");
		}
	}
}
