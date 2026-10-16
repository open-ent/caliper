package fr.openent.caliper.controller;

import fr.openent.caliper.CaliperContext;
import fr.openent.caliper.api.Http;
import fr.openent.caliper.core.CaliperException;
import fr.openent.caliper.core.Uris;
import fr.openent.caliper.core.Vocabulary;
import fr.openent.caliper.model.Envelope;
import fr.openent.caliper.security.Client;
import fr.openent.caliper.security.Scopes;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Le point de dépôt Caliper, sous {@code /ims/caliper/v1p2}.
 *
 * <p>Caliper ne fixe pas le chemin d'un endpoint — un capteur reçoit son adresse de sa
 * configuration — mais il fixe tout le reste : l'enveloppe, son {@code dataVersion}, et le fait
 * qu'un jeton porteur la protège.</p>
 *
 * <p>Une enveloppe fautive est <strong>refusée en entier</strong>, avec le rang et la raison de
 * l'événement en cause. Accepter en écartant silencieusement quelques événements produirait des
 * comptes faux que personne ne remarquerait ; un refus, lui, est renvoyé par le capteur après
 * correction.</p>
 */
public class EndpointController extends BaseController {

	private static final String[] CREATE = {Scopes.EVENT_CREATE};
	private static final String[] READ = {Scopes.EVENT_READONLY};

	private final CaliperContext ctx;

	public EndpointController(CaliperContext ctx) {
		this.ctx = ctx;
		this.pathPrefix = Uris.API;
	}

	@Post("/events")
	public void receive(HttpServerRequest request) {
		ctx.auth.authenticate(request, CREATE).compose(client -> Http.json(request)
				.compose(body -> {
					final Envelope envelope;
					try {
						envelope = Envelope.parse(body);
					} catch (CaliperException e) {
						return Future.<JsonObject>failedFuture(e);
					}
					if (envelope.events().isEmpty()) {
						return Future.succeededFuture(new JsonObject().put("received", 0));
					}
					return ctx.events.store(envelope.events(), envelope.sensor(), client.getId())
							.map(v -> new JsonObject()
									.put("received", envelope.events().size())
									.put("sensor", envelope.sensor()));
				}))
				// 201 : l'enveloppe est acceptée dans son entier, ou elle ne l'est pas du tout.
				.onSuccess(result -> Http.json(request, 201, result))
				.onFailure(error -> Http.error(request, error));
	}

	@Get("/events")
	public void events(HttpServerRequest request) {
		ctx.auth.authenticate(request, READ).compose(client -> {
			final int limit;
			final int offset;
			try {
				limit = Http.limit(request, ctx.maxLimit);
				offset = Http.offset(request);
			} catch (CaliperException e) {
				return Future.<JsonArray>failedFuture(e);
			}
			final String type = request.params().get("type");
			if (type != null && !Vocabulary.isKnownEvent(type)) {
				return Future.<JsonArray>failedFuture(
						new CaliperException("unknown.event.type:" + type));
			}
			return ctx.events.events(request.params().get("actor"), type, limit, offset);
		}).onSuccess(rows -> {
			final JsonArray data = new JsonArray();
			for (int i = 0; i < rows.size(); i++) {
				final Object stored = rows.getJsonObject(i).getValue("data");
				data.add(stored instanceof JsonObject ? stored
						: new JsonObject(String.valueOf(stored)));
			}
			// On rend une enveloppe : ce qu'on a reçu, on le rend sous la même forme.
			Http.json(request, 200, new JsonObject()
					.put("sensor", ctx.uris.sensor())
					.put("sendTime", java.time.Instant.now().toString())
					.put("dataVersion", Vocabulary.CONTEXT)
					.put("data", data));
		}).onFailure(error -> Http.error(request, error));
	}
}
