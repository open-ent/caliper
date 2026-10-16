package fr.openent.caliper.controller;

import fr.openent.caliper.CaliperContext;
import fr.openent.caliper.api.Http;
import fr.openent.caliper.core.CaliperException;
import fr.openent.caliper.core.Uris;
import fr.openent.caliper.core.Vocabulary;
import fr.openent.caliper.security.Scopes;
import fr.openent.caliper.security.Secrets;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.ArrayList;
import java.util.UUID;

/** Administration : clients OAuth, état du capteur, volumétrie. Super administrateur. */
public class AdminController extends BaseController {

	private final CaliperContext ctx;

	public AdminController(CaliperContext ctx) {
		this.ctx = ctx;
		this.pathPrefix = Uris.APP;
	}

	@Get("/admin/configuration")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void configuration(HttpServerRequest request) {
		final JsonArray endpoints = new JsonArray();
		for (JsonObject endpoint : ctx.sensor.endpoints()) {
			// Jamais le jeton : il ouvre la destination à qui le lit.
			endpoints.add(new JsonObject().put("url", endpoint.getString("url"))
					.put("batch-size", endpoint.getInteger("batch-size", 500)));
		}
		renderJson(request, new JsonObject()
				.put("endpoint", ctx.uris.endpoint())
				.put("token_endpoint", ctx.uris.tokenEndpoint())
				.put("dataVersion", Vocabulary.CONTEXT)
				.put("scopes", new JsonArray(new ArrayList<Object>(Scopes.ALL)))
				.put("event_types", new JsonArray(new ArrayList<Object>(Vocabulary.eventTypes())))
				.put("sensor_endpoints", endpoints));
	}

	@Get("/admin/stats")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void stats(HttpServerRequest request) {
		ctx.events.counts()
				.onSuccess(counts -> renderJson(request, counts == null ? new JsonObject() : counts))
				.onFailure(error -> Http.error(request, error));
	}

	@Get("/admin/sensor")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void sensorState(HttpServerRequest request) {
		ctx.events.sensorStates()
				.onSuccess(rows -> renderJson(request, new JsonObject().put("endpoints", rows)))
				.onFailure(error -> Http.error(request, error));
	}

	/** Déclenche un tour de transmission sans attendre la période. */
	@Post("/admin/sensor/run")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void runSensor(HttpServerRequest request) {
		ctx.sensor.runOnce()
				.onSuccess(report -> Http.json(request, 200, report))
				.onFailure(error -> Http.error(request, error));
	}

	@Get("/admin/clients")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void clients(HttpServerRequest request) {
		ctx.clients.list()
				.onSuccess(rows -> renderJson(request, new JsonObject().put("clients", rows)))
				.onFailure(error -> Http.error(request, error));
	}

	@Post("/admin/clients")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void createClient(HttpServerRequest request) {
		user(request).compose(user -> Http.json(request).compose(body -> {
			if (body.getString("name") == null || body.getString("name").trim().isEmpty()) {
				return Future.<JsonObject>failedFuture(new CaliperException("client.name.required"));
			}
			final String id = "caliper-" + UUID.randomUUID();
			final String secret = Secrets.random(32);
			return ctx.clients.create(id, body, Secrets.hash(secret), user.getUserId())
					// Le secret n'est montré qu'ici : seule son empreinte est conservée.
					.map(row -> row.copy().put("client_secret", secret));
		}))
				.onSuccess(row -> Http.json(request, 201, row))
				.onFailure(error -> Http.error(request, error));
	}

	@Delete("/admin/clients/:id")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void deleteClient(HttpServerRequest request) {
		ctx.clients.delete(request.params().get("id"))
				.onSuccess(rows -> {
					if (rows.isEmpty()) {
						Http.error(request, CaliperException.notFound("client"));
						return;
					}
					Http.noContent(request);
				})
				.onFailure(error -> Http.error(request, error));
	}

	private Future<UserInfos> user(HttpServerRequest request) {
		final Promise<UserInfos> promise = Promise.promise();
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				promise.fail(new CaliperException(401, "unauthorized"));
			} else {
				promise.complete(user);
			}
		});
		return promise.future();
	}
}
