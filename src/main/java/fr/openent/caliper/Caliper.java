package fr.openent.caliper;

import fr.openent.caliper.controller.AdminController;
import fr.openent.caliper.controller.EndpointController;
import fr.openent.caliper.controller.TokenController;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.entcore.common.http.BaseServer;

/**
 * Module Caliper Analytics 1.2 (1EdTech) : Open ENT point de dépôt d'événements d'apprentissage,
 * et capteur de ses propres événements vers des destinations tierces.
 */
public class Caliper extends BaseServer {

	@Override
	public void start(final Promise<Void> startPromise) throws Exception {
		final Promise<Void> promise = Promise.promise();
		super.start(promise);
		promise.future()
				.compose(v -> initCaliper(new CaliperContext(vertx, vertx.eventBus(), config)))
				.onComplete(startPromise);
	}

	private Future<Void> initCaliper(CaliperContext context) {
		return addController(new TokenController(context))
				.compose(v -> addController(new EndpointController(context)))
				.compose(v -> addController(new AdminController(context)))
				.map(v -> {
					context.sensor.start();
					return null;
				})
				.mapEmpty();
	}
}
