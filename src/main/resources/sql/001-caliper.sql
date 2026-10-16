CREATE SCHEMA IF NOT EXISTS caliper;

CREATE TABLE caliper.scripts (
	filename VARCHAR(255) NOT NULL PRIMARY KEY,
	passed TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Clients OAuth 2.0 (client credentials) autorisés à déposer ou lire des événements.
-- Caliper ne normalise aucun scope : les nôtres sont donc les nôtres, et documentés comme tels.
CREATE TABLE caliper.client (
	id VARCHAR(64) NOT NULL PRIMARY KEY,
	name VARCHAR(255) NOT NULL,
	secret_hash VARCHAR(128) NOT NULL,
	scopes JSONB NOT NULL DEFAULT '[]',
	enabled BOOLEAN NOT NULL DEFAULT TRUE,
	created_by VARCHAR(36),
	created_at TIMESTAMP NOT NULL DEFAULT NOW(),
	updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Les événements reçus. L'événement complet est conservé tel quel dans « data » : c'est lui
-- qui fait foi, les colonnes ne sont là que pour chercher sans désérialiser.
CREATE TABLE caliper.event (
	id VARCHAR(128) NOT NULL PRIMARY KEY,
	sensor TEXT NOT NULL,
	client_id VARCHAR(64),
	event_type VARCHAR(64) NOT NULL,
	action VARCHAR(64) NOT NULL,
	actor_id TEXT NOT NULL,
	object_id TEXT NOT NULL,
	event_time TIMESTAMP NOT NULL,
	received_at TIMESTAMP NOT NULL DEFAULT NOW(),
	data JSONB NOT NULL
);
CREATE INDEX event_time_idx ON caliper.event (event_time DESC);
CREATE INDEX event_actor ON caliper.event (actor_id, event_time DESC);
CREATE INDEX event_type_idx ON caliper.event (event_type, event_time DESC);

-- Où en est le capteur dans la lecture des événements de l'ENT. Une seule ligne par
-- destination : deux destinations avancent indépendamment, et l'une en panne ne retarde pas
-- l'autre.
CREATE TABLE caliper.sensor_state (
	endpoint TEXT NOT NULL PRIMARY KEY,
	last_event_date TIMESTAMP,
	last_run_at TIMESTAMP,
	last_status VARCHAR(16),
	last_report JSONB NOT NULL DEFAULT '{}',
	sent BIGINT NOT NULL DEFAULT 0,
	failures INTEGER NOT NULL DEFAULT 0
);
