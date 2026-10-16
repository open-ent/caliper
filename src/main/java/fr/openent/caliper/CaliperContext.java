package fr.openent.caliper;

import fr.openent.caliper.core.Uris;
import fr.openent.caliper.model.Events;
import fr.openent.caliper.security.AccessTokens;
import fr.openent.caliper.security.Authenticator;
import fr.openent.caliper.security.KeyManager;
import fr.openent.caliper.sensor.SensorService;
import fr.openent.caliper.service.ClientRepository;
import fr.openent.caliper.service.EventRepository;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

/** Dépendances partagées par les contrôleurs du module. */
public class CaliperContext {

	public final Vertx vertx;
	public final EventBus eb;
	public final JsonObject config;
	public final String publicBase;
	public final long accessTokenTtl;
	public final int maxLimit;
	public final Uris uris;
	public final KeyManager keys;
	public final AccessTokens tokens;
	public final Events builder;
	public final ClientRepository clients = new ClientRepository();
	public final EventRepository events = new EventRepository();
	public final Authenticator auth;
	public final SensorService sensor;

	public CaliperContext(Vertx vertx, EventBus eb, JsonObject config) {
		final JsonObject settings = config.getJsonObject("caliper", new JsonObject());
		this.vertx = vertx;
		this.eb = eb;
		this.config = settings;
		this.publicBase = stripTrailingSlash(settings.getString("issuer",
				config.getString("host", "http://localhost:8090")));
		this.accessTokenTtl = settings.getLong("access-token-ttl-seconds", 3600L);
		this.maxLimit = settings.getInteger("max-limit", 1000);
		this.uris = new Uris(publicBase);
		this.keys = KeyManager.fromConfig(settings);
		this.tokens = new AccessTokens(keys, publicBase);
		this.builder = new Events(uris);
		this.auth = new Authenticator(tokens, clients);
		this.sensor = new SensorService(this);
	}

	public String tokenEndpoint() {
		return uris.tokenEndpoint();
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
