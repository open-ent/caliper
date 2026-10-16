package fr.openent.caliper.sensor;

import fr.openent.caliper.model.Events;
import io.vertx.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduction des événements que l'ENT enregistre déjà en événements Caliper.
 *
 * <p>Classe <strong>pure</strong>. La source est la table {@code events.access_events} du
 * magasin d'événements d'entcore, dont les colonnes sont : {@code id}, {@code date},
 * {@code profile}, {@code module}, {@code event_type}, {@code ua}, {@code device_type},
 * {@code platform_id}, {@code user_id}.</p>
 *
 * <p><strong>Deux types sont traduits, et deux seulement.</strong> {@code ACCESS} et
 * {@code ACCESS_ADAPTER} deviennent un {@code ToolUseEvent} / {@code Used} ; {@code LOGIN}
 * devient un {@code SessionEvent} / {@code LoggedIn}. Les autres — {@code ACTIVATION},
 * {@code SMS}, les tentatives de connexion refusées — sont <strong>comptés et écartés</strong>.</p>
 *
 * <p>Ce n'est pas un oubli. Le vocabulaire d'actions de Caliper est fermé, et aucun de ses
 * profils ne décrit l'activation d'un compte ou un échec d'authentification. Leur coller une
 * action approchante produirait des événements conformes en apparence, que les magasins
 * compteraient avec les autres — un faux qui ne se voit pas. Les écarter en les comptant laisse
 * le compte juste et le manque visible.</p>
 */
public final class AccessEventMapper {

	public static final String ACCESS = "ACCESS";
	public static final String ACCESS_ADAPTER = "ACCESS_ADAPTER";
	public static final String LOGIN = "LOGIN";

	private final Events events;

	public AccessEventMapper(Events events) {
		this.events = events;
	}

	/** Le résultat d'une traduction de lot : ce qui passe, et ce qui ne passe pas. */
	public static final class Report {
		private int translated;
		private final Map<String, Integer> skipped = new LinkedHashMap<String, Integer>();

		public int translated() {
			return translated;
		}

		/** Les types d'événement écartés, et combien de fois. */
		public Map<String, Integer> skipped() {
			return skipped;
		}

		public JsonObject toJson() {
			final JsonObject counts = new JsonObject();
			for (Map.Entry<String, Integer> entry : skipped.entrySet()) {
				counts.put(entry.getKey(), entry.getValue());
			}
			return new JsonObject().put("translated", translated).put("skipped", counts);
		}

		void skip(String type) {
			final String key = type == null ? "(sans type)" : type;
			final Integer previous = skipped.get(key);
			skipped.put(key, previous == null ? 1 : previous + 1);
		}
	}

	/**
	 * Traduit une ligne.
	 *
	 * @return l'événement Caliper, ou {@code null} si ce type n'a pas d'équivalent — le rapport
	 *         en garde alors le compte.
	 */
	public JsonObject map(JsonObject row, Report report) {
		final String type = row.getString("event_type");
		final String userId = row.getString("user_id");
		final String eventTime = dateTime(row.getString("date"));
		if (userId == null || eventTime == null) {
			report.skip("(incomplet)");
			return null;
		}

		final JsonObject event;
		if (ACCESS.equals(type) || ACCESS_ADAPTER.equals(type)) {
			final String module = row.getString("module");
			if (module == null || module.isEmpty()) {
				report.skip("(accès sans module)");
				return null;
			}
			event = events.toolUse(userId, module, eventTime);
		} else if (LOGIN.equals(type)) {
			event = events.login(userId, eventTime);
		} else {
			report.skip(type);
			return null;
		}

		report.translated++;
		return Events.withExtensions(event, extensions(row));
	}

	/**
	 * Ce que l'ENT sait de plus, rangé sous {@code extensions}.
	 *
	 * <p>Seul endroit où la norme admet des propriétés qui ne sont pas les siennes. Le profil et
	 * le type d'appareil sont ce qui rend le flux exploitable pour un établissement ; ils n'ont
	 * pas d'équivalent dans le vocabulaire.</p>
	 */
	private JsonObject extensions(JsonObject row) {
		final JsonObject extensions = new JsonObject();
		putIfPresent(extensions, "profile", row.getString("profile"));
		putIfPresent(extensions, "deviceType", row.getString("device_type"));
		putIfPresent(extensions, "platformId", row.getString("platform_id"));
		return extensions;
	}

	/**
	 * Horodatage ISO-8601 en temps universel.
	 *
	 * <p>mod-postgresql rend {@code 2026-10-16 09:12:00.000} ; Caliper veut un {@code T} et un
	 * fuseau. Sans le {@code Z}, le magasin range l'événement dans son propre fuseau et les
	 * courbes se décalent d'une heure deux fois par an.</p>
	 */
	public static String dateTime(String timestamp) {
		if (timestamp == null || timestamp.isEmpty()) {
			return null;
		}
		String value = timestamp.trim().replace(' ', 'T');
		final int dot = value.indexOf('.');
		if (dot > 0) {
			value = value.substring(0, dot);
		}
		if (value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$")) {
			return value;
		}
		return value + "Z";
	}

	private static void putIfPresent(JsonObject target, String key, String value) {
		if (value != null && !value.isEmpty()) {
			target.put(key, value);
		}
	}
}
