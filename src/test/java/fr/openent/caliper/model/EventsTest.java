package fr.openent.caliper.model;

import fr.openent.caliper.core.Uris;
import fr.openent.caliper.core.Vocabulary;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventsTest {

	private static final String BASE = "https://ent.example.org";
	private static final String USER = "44444444-4444-4444-8444-444444444444";

	private final Events events = new Events(new Uris(BASE));

	@Test
	public void chaque_evenement_porte_son_propre_contexte() {
		// La norme l'exige : chaque élément du tableau data doit être un document JSON-LD
		// autonome, même dans une enveloppe qui déclare déjà sa version.
		JsonObject event = events.toolUse(USER, "blog", "2026-10-16T09:12:00Z");
		assertEquals(Vocabulary.CONTEXT, event.getString("@context"));
	}

	@Test
	public void un_evenement_porte_les_six_proprietes_obligatoires() {
		JsonObject event = events.toolUse(USER, "blog", "2026-10-16T09:12:00Z");
		for (String property : Arrays.asList("id", "type", "actor", "action", "object", "eventTime")) {
			assertTrue("propriété manquante : " + property, event.containsKey(property));
		}
		assertTrue(event.getString("id").startsWith("urn:uuid:"));
		assertEquals(Vocabulary.TOOL_USE_EVENT, event.getString("type"));
		assertEquals(Vocabulary.USED, event.getString("action"));
	}

	@Test
	public void l_acteur_est_une_urn_et_non_une_adresse_web() {
		// Une URL apprendrait au magasin d'analytique que cette personne a un compte ici.
		JsonObject actor = events.toolUse(USER, "blog", "2026-10-16T09:12:00Z").getJsonObject("actor");
		assertEquals("urn:uuid:" + USER, actor.getString("id"));
		assertEquals(Vocabulary.PERSON, actor.getString("type"));
	}

	@Test
	public void un_usage_de_module_designe_le_module_comme_objet() {
		JsonObject object = events.toolUse(USER, "blog", "2026-10-16T09:12:00Z").getJsonObject("object");
		assertEquals(BASE + "/blog", object.getString("id"));
		assertEquals(Vocabulary.SOFTWARE_APPLICATION, object.getString("type"));
		assertEquals("blog", object.getString("name"));
	}

	@Test
	public void une_connexion_designe_la_plateforme_comme_objet() {
		JsonObject event = events.login(USER, "2026-10-16T09:12:00Z");
		assertEquals(Vocabulary.SESSION_EVENT, event.getString("type"));
		assertEquals(Vocabulary.LOGGED_IN, event.getString("action"));
		assertEquals(BASE, event.getJsonObject("object").getString("id"));
	}

	@Test
	public void une_action_inadmissible_pour_le_type_est_refusee_a_la_construction() {
		// Un couple invalide produit un événement que les magasins rejettent — ou, pire,
		// comptent de travers. Mieux vaut échouer ici.
		try {
			events.event(Vocabulary.TOOL_USE_EVENT, Vocabulary.LOGGED_IN,
					events.person(USER), events.edApp(), "2026-10-16T09:12:00Z");
			fail("LoggedIn n'est pas une action d'un ToolUseEvent");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("inadmissible"));
		}
	}

	@Test
	public void le_type_generique_accepte_toute_action_non_vide() {
		// Porte de sortie laissée par la norme pour ce qu'aucun profil ne décrit.
		assertTrue(Vocabulary.allows(Vocabulary.EVENT, "Posted"));
		assertTrue(!Vocabulary.allows(Vocabulary.EVENT, ""));
		assertTrue(!Vocabulary.allows("InventedEvent", "Used"));
	}

	@Test
	public void l_enveloppe_porte_ses_quatre_proprietes_et_pas_une_de_plus() {
		JsonObject envelope = events.envelope("2026-10-16T09:12:00Z",
				Collections.singletonList(events.toolUse(USER, "blog", "2026-10-16T09:12:00Z")));
		assertEquals(4, envelope.size());
		assertEquals(BASE + "/caliper", envelope.getString("sensor"));
		assertEquals(Vocabulary.CONTEXT, envelope.getString("dataVersion"));
		assertEquals(1, envelope.getJsonArray("data").size());
	}

	@Test
	public void les_informations_hors_norme_vont_sous_extensions() {
		JsonObject event = Events.withExtensions(
				events.toolUse(USER, "blog", "2026-10-16T09:12:00Z"),
				new JsonObject().put("profile", "Student"));
		assertEquals("Student", event.getJsonObject("extensions").getString("profile"));
		// Une extension vide ne doit pas créer la clé.
		JsonObject bare = Events.withExtensions(
				events.toolUse(USER, "blog", "2026-10-16T09:12:00Z"), new JsonObject());
		assertTrue(!bare.containsKey("extensions"));
	}

	@Test
	public void une_enveloppe_produite_est_relue_sans_reproche() throws Exception {
		JsonArray data = new JsonArray()
				.add(events.toolUse(USER, "blog", "2026-10-16T09:12:00Z"))
				.add(events.login(USER, "2026-10-16T09:00:00Z"));
		JsonObject envelope = events.envelope("2026-10-16T09:12:00Z",
				Arrays.asList(data.getJsonObject(0), data.getJsonObject(1)));
		assertEquals(2, Envelope.parse(envelope).events().size());
	}
}
