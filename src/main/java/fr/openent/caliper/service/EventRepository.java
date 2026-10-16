package fr.openent.caliper.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Lecture et écriture des événements reçus, et de l'avancement du capteur. */
public class EventRepository {

	private static final String COLUMNS = "id, sensor, client_id, event_type, action, actor_id, "
			+ "object_id, event_time, received_at, data";

	/**
	 * Enregistre un lot d'événements, en une transaction.
	 *
	 * <p>Un conflit d'identifiant ne fait pas échouer le lot : un capteur qui réémet après un
	 * incident de réseau renvoie les mêmes événements, et les compter deux fois fausserait les
	 * courbes aussi sûrement que les perdre. {@code ON CONFLICT DO NOTHING} rend donc le dépôt
	 * idempotent, ce qu'un identifiant d'événement — un UUID posé par le capteur — permet.</p>
	 */
	public Future<JsonArray> store(List<JsonObject> events, String sensor, String clientId) {
		final JsonArray statements = new JsonArray();
		for (JsonObject event : events) {
			statements.add(Db.statement("INSERT INTO caliper.event (id, sensor, client_id, "
					+ "event_type, action, actor_id, object_id, event_time, data) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?::jsonb) "
					+ "ON CONFLICT (id) DO NOTHING",
					new JsonArray()
							.add(event.getString("id"))
							.add(sensor)
							.add(clientId)
							.add(event.getString("type"))
							.add(event.getString("action"))
							.add(entityId(event, "actor"))
							.add(entityId(event, "object"))
							.add(event.getString("eventTime"))
							.add(event.encode())));
		}
		return Db.transaction(statements);
	}

	/** Un acteur ou un objet peut être cité par son seul IRI ou par un objet qui le porte. */
	static String entityId(JsonObject event, String property) {
		final Object value = event.getValue(property);
		if (value instanceof String) {
			return (String) value;
		}
		return value instanceof JsonObject ? ((JsonObject) value).getString("id") : null;
	}

	public Future<JsonArray> events(String actorId, String eventType, int limit, int offset) {
		final StringBuilder query = new StringBuilder("SELECT " + COLUMNS + " FROM caliper.event WHERE TRUE");
		final JsonArray params = new JsonArray();
		if (actorId != null) {
			query.append(" AND actor_id = ?");
			params.add(actorId);
		}
		if (eventType != null) {
			query.append(" AND event_type = ?");
			params.add(eventType);
		}
		query.append(" ORDER BY event_time DESC LIMIT ? OFFSET ?");
		params.add(limit).add(offset);
		return Db.sql(query.toString(), params);
	}

	public Future<JsonObject> counts() {
		return Db.sqlOne("SELECT COUNT(*)::int AS total, "
				+ "COUNT(DISTINCT actor_id)::int AS actors, "
				+ "MIN(event_time) AS first_event, MAX(event_time) AS last_event "
				+ "FROM caliper.event", new JsonArray());
	}

	// ------------------------------------------------------------------ capteur

	/**
	 * Les accès de l'ENT non encore transmis à cette destination.
	 *
	 * <p>Lecture dans le schéma du magasin d'événements d'entcore. Si l'ENT est configuré sans
	 * magasin PostgreSQL, la requête échoue et le capteur se tait plutôt que de faire échouer
	 * le module.</p>
	 */
	public Future<JsonArray> accessEventsSince(String since, int batchSize) {
		final JsonArray params = new JsonArray();
		final StringBuilder query = new StringBuilder(
				"SELECT id, date, profile, module, event_type, device_type, platform_id, user_id "
				+ "FROM events.access_events WHERE TRUE");
		if (since != null) {
			query.append(" AND date > ?::timestamp");
			params.add(since);
		}
		query.append(" ORDER BY date ASC LIMIT ?");
		params.add(batchSize);
		return Db.sql(query.toString(), params).recover(error -> Future.succeededFuture(null));
	}

	public Future<JsonObject> sensorState(String endpoint) {
		return Db.sqlOne("SELECT endpoint, last_event_date, last_run_at, last_status, "
				+ "last_report, sent, failures FROM caliper.sensor_state WHERE endpoint = ?",
				new JsonArray().add(endpoint));
	}

	public Future<JsonArray> sensorStates() {
		return Db.sql("SELECT endpoint, last_event_date, last_run_at, last_status, last_report, "
				+ "sent, failures FROM caliper.sensor_state ORDER BY endpoint", new JsonArray());
	}

	/**
	 * Avance le curseur du capteur.
	 *
	 * <p>Il n'avance qu'<strong>après</strong> une transmission réussie. En cas d'échec, le
	 * compteur d'échecs monte et le curseur reste où il est : le lot repart au tour suivant.
	 * Avancer d'abord perdrait les événements d'une destination momentanément injoignable.</p>
	 */
	public Future<JsonArray> saveSensorState(String endpoint, String lastEventDate, String status,
											 JsonObject report, int sentDelta, boolean failure) {
		return Db.sql("INSERT INTO caliper.sensor_state (endpoint, last_event_date, last_run_at, "
				+ "last_status, last_report, sent, failures) "
				+ "VALUES (?, ?::timestamp, NOW(), ?, ?::jsonb, ?, ?) "
				+ "ON CONFLICT (endpoint) DO UPDATE SET "
				+ "last_event_date = COALESCE(EXCLUDED.last_event_date, caliper.sensor_state.last_event_date), "
				+ "last_run_at = NOW(), last_status = EXCLUDED.last_status, "
				+ "last_report = EXCLUDED.last_report, "
				+ "sent = caliper.sensor_state.sent + EXCLUDED.sent, "
				+ "failures = CASE WHEN ? THEN caliper.sensor_state.failures + 1 ELSE 0 END",
				new JsonArray().add(endpoint).add(lastEventDate).add(status)
						.add(Db.encode(report)).add(sentDelta).add(failure ? 1 : 0).add(failure));
	}

	public static List<JsonObject> rows(JsonArray array) {
		final List<JsonObject> list = new ArrayList<JsonObject>();
		if (array == null) {
			return list;
		}
		for (int i = 0; i < array.size(); i++) {
			list.add(array.getJsonObject(i));
		}
		return list;
	}
}
