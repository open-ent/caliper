package fr.openent.caliper.api;

import fr.openent.caliper.core.CaliperException;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/** Réponses HTTP du module : JSON, pagination, erreurs 1EdTech. */
public final class Http {

	private static final Logger log = LoggerFactory.getLogger(Http.class);
	private static final int MAX_BODY = 4 * 1024 * 1024;
	public static final int DEFAULT_LIMIT = 100;

	private Http() {
	}

	public static Future<MultiMap> form(HttpServerRequest request) {
		final Promise<MultiMap> promise = Promise.promise();
		request.setExpectMultipart(true);
		request.exceptionHandler(promise::tryFail);
		request.endHandler(v -> promise.tryComplete(request.formAttributes()));
		resume(request);
		return promise.future();
	}

	public static Future<JsonObject> json(HttpServerRequest request) {
		final Promise<JsonObject> promise = Promise.promise();
		request.exceptionHandler(promise::tryFail);
		request.bodyHandler(buffer -> {
			if (buffer.length() > MAX_BODY) {
				promise.tryFail(new CaliperException(413, "body.too.large"));
				return;
			}
			try {
				promise.tryComplete(new JsonObject(buffer.toString("UTF-8")));
			} catch (RuntimeException e) {
				promise.tryFail(new CaliperException("invalid.json"));
			}
		});
		resume(request);
		return promise.future();
	}

	public static void json(HttpServerRequest request, int status, Object body) {
		final String payload = body instanceof JsonArray
				? ((JsonArray) body).encode() : ((JsonObject) body).encode();
		request.response()
				.setStatusCode(status)
				.putHeader("Content-Type", "application/json; charset=utf-8")
				.putHeader("Cache-Control", "no-store")
				.end(payload);
	}

	/** Une ressource publique peut être mise en cache : elle ne dépend d'aucune session. */
	public static void publicJson(HttpServerRequest request, Object body) {
		final String payload = body instanceof JsonArray
				? ((JsonArray) body).encode() : ((JsonObject) body).encode();
		request.response()
				.setStatusCode(200)
				.putHeader("Content-Type", "application/json; charset=utf-8")
				.putHeader("Cache-Control", "public, max-age=300")
				.end(payload);
	}

	public static void noContent(HttpServerRequest request) {
		request.response().setStatusCode(204).end();
	}

	/** {@code limit} et {@code offset} de l'API Caliper, avec un plafond. */
	public static int limit(HttpServerRequest request, int maxLimit) throws CaliperException {
		return Math.min(integer(request.params().get("limit"), DEFAULT_LIMIT, 1), maxLimit);
	}

	public static int offset(HttpServerRequest request) throws CaliperException {
		return integer(request.params().get("offset"), 0, 0);
	}

	private static int integer(String value, int defaultValue, int min) throws CaliperException {
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			final int parsed = Integer.parseInt(value.trim());
			if (parsed < min) {
				throw new CaliperException("invalid.pagination");
			}
			return parsed;
		} catch (NumberFormatException e) {
			throw new CaliperException("invalid.pagination");
		}
	}

	public static void error(HttpServerRequest request, Throwable error) {
		final CaliperException e;
		if (error instanceof CaliperException) {
			e = (CaliperException) error;
		} else {
			log.error("[Caliper] Unexpected error", error);
			e = new CaliperException(500, "server_error");
		}
		if (e.getStatus() == 401) {
			request.response().putHeader("WWW-Authenticate", "Bearer");
		}
		json(request, e.getStatus(), new JsonObject()
				.put("imsx_codeMajor", "failure")
				.put("imsx_severity", "error")
				.put("imsx_description", e.getMessage())
				.put("imsx_CodeMinor", new JsonObject().put("imsx_codeMinorField",
						new JsonArray().add(new JsonObject()
								.put("imsx_codeMinorFieldName", "TargetEndSystem")
								.put("imsx_codeMinorFieldValue", e.getCodeMinor())))));
	}

	private static void resume(HttpServerRequest request) {
		try {
			request.resume();
		} catch (Exception ignored) {
			// déjà consommé
		}
	}
}
