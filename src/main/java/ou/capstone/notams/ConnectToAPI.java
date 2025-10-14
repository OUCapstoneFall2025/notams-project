package ou.capstone.notams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Connects to the FAA NOTAM API to fetch and parse NOTAM data.
 */
public final class ConnectToAPI {

    private static final Logger logger = LoggerFactory.getLogger(ConnectToAPI.class);

    private static final String FAA_DOMAIN = "external-api.faa.gov";
    private static final String NOTAM_API_PATH = "/notamapi/v1/notams";
    private static final String RESPONSE_FORMAT = "geoJson";
    private static final String DEFAULT_ICAO = "KOKC";  // TODO: replace with UserAirportInput later
    private static final String DEFAULT_PAGE_SIZE = "50";
    private static final String DEFAULT_PAGE_NUM = "1";
    private static final String SORT_BY = "effectiveStartDate";
    private static final String SORT_ORDER = "Desc";

    /**
     * Fetches NOTAMs from the FAA API for the default ICAO location.
     *
     * @return List of parsed Notam objects from the FAA API response
     * @throws Exception if API call fails or credentials are missing
     */
    public static List<Notam> fetchNotams() throws Exception {
        return fetchNotams(DEFAULT_ICAO);
    }

    /**
     * Fetches NOTAMs from the FAA API for a specific ICAO location.
     *
     * @param icaoLocation The ICAO airport code (e.g., "KOKC")
     * @return List of parsed Notam objects from the FAA API response
     * @throws Exception if API call fails or credentials are missing
     */
    public static List<Notam> fetchNotams(final String icaoLocation) throws Exception {
        logger.info("Starting NOTAM fetch for ICAO location: {}", icaoLocation);

        final String clientId = System.getenv("FAA_CLIENT_ID");
        final String clientSecret = System.getenv("FAA_CLIENT_SECRET");

        if (clientId == null || clientSecret == null) {
            logger.error("FAA API credentials not found in environment variables");
            throw new IllegalStateException("FAA_CLIENT_ID or FAA_CLIENT_SECRET not set in environment!");
        }

        final String queryString = String.format(
                "responseFormat=%s&icaoLocation=%s&pageSize=%s&pageNum=%s&sortBy=%s&sortOrder=%s",
                enc(RESPONSE_FORMAT),
                enc(icaoLocation),
                enc(DEFAULT_PAGE_SIZE),
                enc(DEFAULT_PAGE_NUM),
                enc(SORT_BY),
                enc(SORT_ORDER)
        );

        final URI uri = new URI("https", FAA_DOMAIN, NOTAM_API_PATH, queryString, null);
        logger.debug("Requesting URL: {}", uri);

        final HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("client_id", clientId)
                .header("client_secret", clientSecret)
                .build();

        final HttpClient http = HttpClient.newHttpClient();
        final HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

        logger.info("FAA API response status: {}", response.statusCode());

        if (response.statusCode() == 200) {
            final List<Notam> notams = new GeoJsonReader().parseNotamsFromGeoJson(response.body());
            logger.info("Successfully fetched and parsed {} NOTAMs", notams.size());
            return notams;
        } else {
            logger.error("FAA API returned non-200 status: {}. URL: {}", response.statusCode(), uri);
            throw new IOException("FAA API returned non-200 status: " + response.statusCode());
        }
    }

    /**
     * Main method for manual testing. For automated tests, see GeoJsonReaderTest.
     */
    public static void main(final String[] args) throws Exception {
        final List<Notam> notams = fetchNotams();
        logger.info("Fetched {} NOTAMs", notams.size());
        for (final Notam notam : notams) {
            logger.info("{}", notam);
        }
    }

    private static String enc(final String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}