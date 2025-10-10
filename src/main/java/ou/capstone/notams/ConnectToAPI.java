package ou.capstone.notams;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectToAPI {
    private static final Logger logger = LoggerFactory.getLogger(ConnectToAPI.class);

    public static void main(String[] args) throws Exception {
        logger.info("Starting FAA API connection");
        
        final String clientId = System.getenv("FAA_CLIENT_ID");
        final String clientSecret = System.getenv("FAA_CLIENT_SECRET");

        if (clientId == null || clientSecret == null) {
            logger.error("FAA authentication credentials not found in environment variables");
            throw new IllegalStateException("FAA_CLIENT_ID or FAA_CLIENT_SECRET not set in environment!");
        }
        
        logger.debug("FAA authentication credentials loaded successfully");

        final String domain = "external-api.faa.gov";
        final String responseFormat = "geoJson";
        final String icaoLocation = "KOKC";   // TODO: replace with UserAirportInput later
        final String pageSize = "50";
        final String pageNum = "1";
        final String sortBy = "effectiveStartDate";
        final String sortOrder = "Desc";

        logger.debug("Building API request parameters - Location: {}, PageSize: {}, PageNum: {}, SortBy: {}, SortOrder: {}", 
                     icaoLocation, pageSize, pageNum, sortBy, sortOrder);

        final String queryString = String.format(
                "responseFormat=%s&icaoLocation=%s&pageSize=%s&pageNum=%s&sortBy=%s&sortOrder=%s",
                enc(responseFormat),
                enc(icaoLocation),
                enc(pageSize),
                enc(pageNum),
                enc(sortBy),
                enc(sortOrder)
        );

        final URI uri = new URI("https", domain, "/notamapi/v1/notams", queryString, null);
        logger.info("Constructed FAA API URI: {}", uri);

        final HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("client_id", clientId)
                .header("client_secret", clientSecret)
                .build();

        logger.debug("HTTP request built successfully with authentication headers");

        final HttpClient http = HttpClient.newHttpClient();
        logger.info("Sending HTTP request to FAA API");
        
        final HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

        logger.info("FAA API response received - Status Code: {}", response.statusCode());
        logger.debug("Response body length: {} characters", response.body().length());
        
        if (response.statusCode() == 200) {
            logger.info("API request successful");
        } else {
            logger.warn("API request returned non-success status code: {}", response.statusCode());
        }

        System.out.println("URL: " + uri);
        System.out.println("Status: " + response.statusCode());
        System.out.println(response.body());
        
        logger.info("FAA API interaction completed");
    }

    private static String enc(final String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}