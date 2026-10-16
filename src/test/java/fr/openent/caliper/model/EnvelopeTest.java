package fr.openent.caliper.model;

import fr.openent.caliper.core.CaliperException;
import fr.openent.caliper.core.Vocabulary;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EnvelopeTest {

	@Test
	public void une_enveloppe_conforme_est_acceptee() throws Exception {
		Envelope envelope = Envelope.parse(envelope(event()));
		assertEquals("https://ent.example.org/caliper", envelope.sensor());
		assertEquals(1, envelope.events().size());
	}

	@Test
	public void une_enveloppe_vide_d_evenements_reste_valide() throws Exception {
		JsonObject body = envelope(event());
		body.put("data", new JsonArray());
		assertTrue(Envelope.parse(body).events().isEmpty());
	}

	@Test
	public void chaque_propriete_obligatoire_manquante_est_signalee() {
		for (String property : new String[]{"sensor", "sendTime", "dataVersion", "data"}) {
			JsonObject body = envelope(event());
			body.remove(property);
			assertRefused(body, "envelope.missing." + property);
		}
	}

	@Test
	public void une_propriete_supplementaire_fait_refuser_l_enveloppe() {
		// « Custom properties are not permitted » : une enveloppe qui en porte vient d'un
		// capteur qui parle un autre dialecte, et le reste est douteux.
		JsonObject body = envelope(event()).put("tenant", "collège");
		assertRefused(body, "envelope.unexpected.property:tenant");
	}

	@Test
	public void une_version_de_donnees_etrangere_fait_refuser_l_enveloppe() {
		JsonObject body = envelope(event())
				.put("dataVersion", "http://purl.imsglobal.org/ctx/caliper/v1p1");
		assertRefused(body, "envelope.unsupported.dataVersion");
	}

	@Test
	public void un_evenement_sans_contexte_fait_refuser_l_enveloppe() {
		// Le contexte de l'enveloppe ne vaut pas pour l'événement : chacun est un document
		// JSON-LD autonome.
		JsonObject event = event();
		event.remove("@context");
		assertRefused(envelope(event), "event.0.missing.@context");
	}

	@Test
	public void une_action_inadmissible_pour_le_type_fait_refuser_l_enveloppe() {
		JsonObject event = event().put("action", Vocabulary.LOGGED_IN);
		assertRefused(envelope(event), "event.0.action.not.allowed");
	}

	@Test
	public void un_type_inconnu_fait_refuser_l_enveloppe() {
		assertRefused(envelope(event().put("type", "InventedEvent")), "event.0.unknown.type");
	}

	@Test
	public void un_acteur_sans_identifiant_fait_refuser_l_enveloppe() {
		JsonObject event = event().put("actor", new JsonObject().put("type", Vocabulary.PERSON));
		assertRefused(envelope(event), "event.0.actor.without.id");
	}

	@Test
	public void une_entite_citee_par_son_seul_iri_est_acceptee() throws Exception {
		// La norme l'admet : un acteur peut n'être qu'une IRI.
		JsonObject event = event().put("actor", "urn:uuid:1234");
		assertEquals(1, Envelope.parse(envelope(event)).events().size());
	}

	@Test
	public void le_rang_de_l_evenement_fautif_est_indique() {
		JsonObject bad = event();
		bad.remove("eventTime");
		JsonObject body = envelope(event());
		body.getJsonArray("data").add(bad);
		// Sans le rang, un capteur qui envoie cinq cents événements ne saurait pas lequel
		// corriger.
		assertRefused(body, "event.1.missing.eventTime");
	}

	@Test
	public void un_element_de_data_qui_n_est_pas_un_objet_est_refuse() {
		JsonObject body = envelope(event());
		body.getJsonArray("data").add("pas un événement");
		assertRefused(body, "envelope.data.1.not.an.object");
	}

	@Test
	public void un_data_qui_n_est_pas_un_tableau_est_refuse() {
		assertRefused(envelope(event()).put("data", new JsonObject()),
				"envelope.data.not.an.array");
	}

	@Test
	public void une_enveloppe_absente_est_refusee() {
		try {
			Envelope.parse(null);
			fail("une enveloppe absente doit être refusée");
		} catch (CaliperException expected) {
			assertEquals("envelope.empty", expected.getMessage());
		}
	}

	// ------------------------------------------------------------------ fixtures

	private static JsonObject envelope(JsonObject event) {
		return new JsonObject()
				.put("sensor", "https://ent.example.org/caliper")
				.put("sendTime", "2026-10-16T09:12:00Z")
				.put("dataVersion", Vocabulary.CONTEXT)
				.put("data", new JsonArray().add(event));
	}

	private static JsonObject event() {
		return new JsonObject()
				.put("@context", Vocabulary.CONTEXT)
				.put("id", "urn:uuid:11111111-1111-4111-8111-111111111111")
				.put("type", Vocabulary.TOOL_USE_EVENT)
				.put("actor", new JsonObject().put("id", "urn:uuid:abc").put("type", Vocabulary.PERSON))
				.put("action", Vocabulary.USED)
				.put("object", new JsonObject().put("id", "https://ent.example.org/blog")
						.put("type", Vocabulary.SOFTWARE_APPLICATION))
				.put("eventTime", "2026-10-16T09:12:00Z");
	}

	private static void assertRefused(JsonObject body, String expectedMessage) {
		try {
			Envelope.parse(body);
			fail("enveloppe acceptée à tort, attendu : " + expectedMessage);
		} catch (CaliperException e) {
			assertTrue("message inattendu : " + e.getMessage(),
					e.getMessage().startsWith(expectedMessage));
			assertEquals(400, e.getStatus());
		}
	}
}
