package fr.openent.caliper.sensor;

import fr.openent.caliper.CaliperContext;
import fr.openent.caliper.model.Events;
import fr.openent.caliper.service.EventRepository;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Le capteur : il lit les événements que l'ENT enregistre déjà et les transmet, en Caliper, aux
 * destinations configurées.
 *
 * <p>Trois propriétés tiennent ce service, et chacune répond à une façon de perdre ou de fausser
 * des données.</p>
 *
 * <p><strong>Le curseur n'avance qu'après une transmission réussie.</strong> Une destination
 * momentanément injoignable ne coûte rien : le lot repart au tour suivant. Avancer d'abord
 * perdrait silencieusement tout ce qui était en vol.</p>
 *
 * <p><strong>Chaque destination a son propre curseur.</strong> Deux destinations avancent
 * indépendamment, et l'une en panne ne retient pas l'autre.</p>
 *
 * <p><strong>Rien n'est transmis deux fois sans être reconnu comme tel.</strong> Chaque
 * événement porte un UUID stable ; un endpoint conforme — dont le nôtre — l'emploie pour
 * ignorer un doublon plutôt que de le compter.</p>
 */
public class SensorService {

	private static final Logger log = LoggerFactory.getLogger(SensorService.class);
	private static final int DEFAULT_BATCH = 500;

	private final CaliperContext ctx;
	private final EventRepository repository;
	private final AccessEventMapper mapper;
	private final HttpClient http;

	public SensorService(CaliperContext ctx) {
		this.ctx = ctx;
		this.repository = ctx.events;
		this.mapper = new AccessEventMapper(ctx.builder);
		this.http = ctx.vertx.createHttpClient(new HttpClientOptions()
				.setConnectTimeout(10_000).setIdleTimeout(60));
	}

	/** Démarre la transmission périodique, si une destination est configurée. */
	public void start() {
		final List<JsonObject> endpoints = endpoints();
		if (endpoints.isEmpty()) {
			log.info("[Caliper] Aucune destination configurée : le capteur reste au repos.");
			return;
		}
		final long interval = ctx.config.getJsonObject("sensor", new JsonObject())
				.getLong("interval-seconds", 300L) * 1000L;
		ctx.vertx.setPeriodic(interval, timer -> runOnce().onFailure(
				error -> log.error("[Caliper] Transmission en échec", error)));
		log.info("[Caliper] Capteur démarré : " + endpoints.size() + " destination(s), toutes les "
				+ (interval / 1000L) + " s.");
	}

	/** Un tour, sur toutes les destinations. Appelable à la main depuis l'administration. */
	public Future<JsonObject> runOnce() {
		final List<JsonObject> endpoints = endpoints();
		if (endpoints.isEmpty()) {
			return Future.succeededFuture(new JsonObject().put("endpoints", 0));
		}
		final List<Future> runs = new ArrayList<Future>();
		final JsonObject report = new JsonObject();
		for (JsonObject endpoint : endpoints) {
			final String url = endpoint.getString("url");
			runs.add(runOne(endpoint).map(result -> {
				report.put(url, result);
				return null;
			}).otherwise(error -> {
				report.put(url, new JsonObject().put("status", "error")
						.put("message", String.valueOf(error.getMessage())));
				return null;
			}));
		}
		return CompositeFuture.join(runs).map(v -> report);
	}

	private Future<JsonObject> runOne(JsonObject endpoint) {
		final String url = endpoint.getString("url");
		final int batchSize = endpoint.getInteger("batch-size", DEFAULT_BATCH);
		return repository.sensorState(url).compose(state -> {
			final String since = state == null ? null : state.getString("last_event_date");
			return repository.accessEventsSince(since, batchSize).compose(rows -> {
				if (rows == null) {
					// Le magasin d'événements PostgreSQL n'est pas en place : se taire plutôt
					// que d'échouer à chaque tour.
					return repository.saveSensorState(url, null, "no-source", new JsonObject()
							.put("message", "events.access_events introuvable"), 0, false)
							.map(v -> new JsonObject().put("status", "no-source"));
				}
				final List<JsonObject> source = EventRepository.rows(rows);
				if (source.isEmpty()) {
					return repository.saveSensorState(url, null, "idle", new JsonObject(), 0, false)
							.map(v -> new JsonObject().put("status", "idle").put("sent", 0));
				}

				final AccessEventMapper.Report mapped = new AccessEventMapper.Report();
				final List<JsonObject> events = new ArrayList<JsonObject>(source.size());
				String lastDate = null;
				for (JsonObject row : source) {
					final JsonObject event = mapper.map(row, mapped);
					if (event != null) {
						events.add(event);
					}
					// Le curseur avance sur TOUTES les lignes lues, y compris celles qu'on
					// écarte : sinon un type non traduit bloquerait le flux pour toujours.
					lastDate = row.getString("date");
				}
				if (events.isEmpty()) {
					return repository.saveSensorState(url, lastDate, "skipped", mapped.toJson(),
							0, false).map(v -> new JsonObject().put("status", "skipped")
									.put("report", mapped.toJson()));
				}

				final JsonObject envelope = ctx.builder.envelope(Instant.now().toString(), events);
				final String finalDate = lastDate;
				return post(url, endpoint.getString("token"), envelope)
						.compose(v -> repository.saveSensorState(url, finalDate, "sent",
										mapped.toJson(), events.size(), false)
								.map(ignored -> new JsonObject().put("status", "sent")
										.put("sent", events.size()).put("report", mapped.toJson())))
						.recover(error -> repository.saveSensorState(url, null, "error",
										new JsonObject().put("message",
												String.valueOf(error.getMessage())), 0, true)
								.compose(ignored -> Future.<JsonObject>failedFuture(error)));
			});
		});
	}

	/** Dépose une enveloppe sur une destination. */
	private Future<Void> post(String url, String token, JsonObject envelope) {
		final Promise<Void> promise = Promise.promise();
		final RequestOptions options = new RequestOptions().setAbsoluteURI(url)
				.setMethod(HttpMethod.POST);
		http.request(options).onFailure(promise::tryFail).onSuccess(request -> {
			request.putHeader("Content-Type", "application/json");
			if (token != null && !token.isEmpty()) {
				request.putHeader("Authorization", "Bearer " + token);
			}
			request.send(Buffer.buffer(envelope.encode()))
					.onFailure(promise::tryFail)
					.onSuccess(response -> {
						final int status = response.statusCode();
						if (status >= 200 && status < 300) {
							promise.tryComplete();
						} else {
							promise.tryFail("endpoint." + status);
						}
					});
		});
		return promise.future();
	}

	public List<JsonObject> endpoints() {
		final JsonArray declared = ctx.config.getJsonObject("sensor", new JsonObject())
				.getJsonArray("endpoints", new JsonArray());
		final List<JsonObject> endpoints = new ArrayList<JsonObject>();
		for (int i = 0; i < declared.size(); i++) {
			final Object value = declared.getValue(i);
			if (value instanceof JsonObject && ((JsonObject) value).getString("url") != null) {
				endpoints.add((JsonObject) value);
			}
		}
		return endpoints;
	}
}
