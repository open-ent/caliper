package fr.openent.caliper.core;

/**
 * Erreur fonctionnelle renvoyée à un client Caliper : code HTTP, code « mineur » 1EdTech
 * (imsx_codeMinorFieldValue) et description.
 */
public class CaliperException extends Exception {

	public static final String INVALID_DATA = "invaliddata";
	public static final String INVALID_FILTER_FIELD = "invalid_filter_field";
	public static final String INVALID_SELECTION_FIELD = "invalid_selection_field";
	public static final String INVALID_SORT_FIELD = "invalid_sort_field";
	public static final String UNAUTHORISED = "unauthorisedrequest";
	public static final String FORBIDDEN = "forbidden";
	public static final String UNKNOWN_OBJECT = "unknownobject";
	public static final String SERVER_ERROR = "internal_server_error";

	private final int status;
	private final String codeMinor;

	public CaliperException(String message) {
		this(400, INVALID_DATA, message);
	}

	public CaliperException(int status, String message) {
		this(status, defaultMinor(status), message);
	}

	public CaliperException(int status, String codeMinor, String message) {
		super(message);
		this.status = status;
		this.codeMinor = codeMinor;
	}

	public static CaliperException notFound(String what) {
		return new CaliperException(404, UNKNOWN_OBJECT, what + ".not.found");
	}

	public int getStatus() {
		return status;
	}

	public String getCodeMinor() {
		return codeMinor;
	}

	private static String defaultMinor(int status) {
		switch (status) {
			case 401:
				return UNAUTHORISED;
			case 403:
				return FORBIDDEN;
			case 404:
				return UNKNOWN_OBJECT;
			default:
				return status >= 500 ? SERVER_ERROR : INVALID_DATA;
		}
	}
}
