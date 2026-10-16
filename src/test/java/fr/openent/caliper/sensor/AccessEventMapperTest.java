package fr.openent.caliper.sensor;

import fr.openent.caliper.core.Uris;
import fr.openent.caliper.core.Vocabulary;
import fr.openent.caliper.model.Events;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AccessEventMapperTest {

	private static final String USER = "44444444-4444-4444-8444-444444444444";

	private final AccessEventMapper mapper =
			new AccessEventMapper(new Events(new Uris("https://ent.example.org")));

	@Test
	public void un_acces_devient_un_usage_de_module() {
		AccessEventMapper.Report report = new AccessEventMapper.Report();
		JsonObject event = mapper.map(row("ACCESS", "blog"), report);
		assertEquals(Vocabulary.TOOL_USE_EVENT, event.getString("type"));
		assertEquals(Vocabulary.USED, event.getString("action"));
		assertEquals("https://ent.example.org/blog", event.getJsonObject("object").getString("id"));
		assertEquals(1, report.translated());
	}

	@Test
	public void un_acces_par_adaptateur_est_un_acces_comme_un_autre() {
		AccessEventMapper.Report report = new AccessEventMapper.Report();
		assertNotNull(mapper.map(row("ACCESS_ADAPTER", "portal"), report));
		assertEquals(1, report.translated());
	}

	@Test
	public void une_connexion_devient_un_evenement_de_session() {
		AccessEventMapper.Report report = new AccessEventMapper.Report();
		JsonObject event = mapper.map(row("LOGIN", null), report);
		assertEquals(Vocabulary.SESSION_EVENT, event.getString("type"));
		assertEquals(Vocabulary.LOGGED_IN, event.getString("action"));
	}

	@Test
	public void les_types_sans_equivalent_sont_comptes_et_ecartes() {
		// Aucun profil de Caliper ne décrit l'activation d'un compte ni un échec
		// d'authentification. Leur coller une action approchante produirait un faux que les
		// magasins compteraient avec les autres.
		AccessEventMapper.Report report = new AccessEventMapper.Report();
		assertNull(mapper.map(row("ACTIVATION", null), report));
		assertNull(mapper.map(row("SMS", null), report));
		assertNull(mapper.map(row("ACTIVATION", null), report));
		assertEquals(0, report.translated());
		assertEquals(Integer.valueOf(2), report.skipped().get("ACTIVATION"));
		assertEquals(Integer.valueOf(1), report.skipped().get("SMS"));
	}

	@Test
	public void un_acces_sans_module_est_ecarte_plutot_que_rattache_n_importe_ou() {
		AccessEventMapper.Report report = new AccessEventMapper.Report();
		assertNull(mapper.map(row("ACCESS", null), report));
		assertEquals(Integer.valueOf(1), report.skipped().get("(accès sans module)"));
	}

	@Test
	public void une_ligne_sans_usager_ou_sans_date_est_ecartee() {
		AccessEventMapper.Report report = new AccessEventMapper.Report();
		assertNull(mapper.map(row("ACCESS", "blog").put("user_id", (String) null), report));
		assertNull(mapper.map(row("ACCESS", "blog").put("date", (String) null), report));
		assertEquals(Integer.valueOf(2), report.skipped().get("(incomplet)"));
	}

	@Test
	public void ce_que_l_ENT_sait_de_plus_va_sous_extensions() {
		AccessEventMapper.Report report = new AccessEventMapper.Report();
		JsonObject extensions = mapper.map(row("ACCESS", "blog"), report)
				.getJsonObject("extensions");
		assertEquals("Student", extensions.getString("profile"));
		assertEquals("desktop", extensions.getString("deviceType"));
		assertEquals("openent", extensions.getString("platformId"));
	}

	@Test
	public void les_horodatages_sont_en_temps_universel() {
		// Sans le Z, le magasin range l'événement dans son propre fuseau et les courbes se
		// décalent d'une heure deux fois par an.
		assertEquals("2026-10-16T09:12:00Z", AccessEventMapper.dateTime("2026-10-16 09:12:00.000"));
		assertEquals("2026-10-16T09:12:00Z", AccessEventMapper.dateTime("2026-10-16T09:12:00Z"));
		assertEquals("2026-10-16T09:12:00+02:00",
				AccessEventMapper.dateTime("2026-10-16T09:12:00+02:00"));
		assertNull(AccessEventMapper.dateTime(null));
	}

	@Test
	public void le_rapport_se_rend_en_json() {
		AccessEventMapper.Report report = new AccessEventMapper.Report();
		mapper.map(row("ACCESS", "blog"), report);
		mapper.map(row("ACTIVATION", null), report);
		JsonObject json = report.toJson();
		assertEquals(1, json.getInteger("translated").intValue());
		assertEquals(1, json.getJsonObject("skipped").getInteger("ACTIVATION").intValue());
		assertFalse(json.getJsonObject("skipped").containsKey("ACCESS"));
		assertTrue(json.containsKey("translated"));
	}

	private static JsonObject row(String type, String module) {
		return new JsonObject()
				.put("id", "11111111-1111-4111-8111-111111111111")
				.put("date", "2026-10-16 09:12:00.000")
				.put("profile", "Student")
				.put("module", module)
				.put("event_type", type)
				.put("device_type", "desktop")
				.put("platform_id", "openent")
				.put("user_id", USER);
	}
}
