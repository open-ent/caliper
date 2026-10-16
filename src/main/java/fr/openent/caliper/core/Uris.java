package fr.openent.caliper.core;

/** Les adresses du service et les identifiants des entités que l'ENT décrit. */
public final class Uris {

	/** Préfixe de l'API normée. */
	public static final String API = "/ims/caliper/v1p2";
	/** Préfixe de l'administration et du point de jeton. */
	public static final String APP = "/caliper";

	private final String base;

	public Uris(String publicBase) {
		this.base = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
	}

	public String base() {
		return base;
	}

	/** L'ENT lui-même, comme application émettrice ({@code edApp}). */
	public String edApp() {
		return base;
	}

	/** Le capteur : l'émetteur de l'enveloppe, et non l'application. */
	public String sensor() {
		return base + APP;
	}

	/**
	 * Une personne.
	 *
	 * <p>Un {@code urn:uuid:} et non une adresse web : l'identifiant d'un acteur doit être
	 * stable, la norme n'exige pas qu'il réponde, et une URL apprendrait à qui lit le flux que
	 * cette personne a un compte ici. Un magasin d'analytique n'a pas à le savoir.</p>
	 */
	public String person(String userId) {
		return "urn:uuid:" + userId;
	}

	/** Un module de l'ENT, comme application utilisée. */
	public String module(String module) {
		return base + "/" + module;
	}

	public String endpoint() {
		return base + API + "/events";
	}

	public String tokenEndpoint() {
		return base + APP + "/oauth/token";
	}
}
