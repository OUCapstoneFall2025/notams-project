package tempNotams;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

public final class ConnectToAPITemp {

    public static void main(String[] args) throws Exception {
        final String clientId = System.getenv("FAA_CLIENT_ID");
        final String clientSecret = System.getenv("FAA_CLIENT_SECRET");

        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("FAA_CLIENT_ID or FAA_CLIENT_SECRET not set in environment!");
        }

        final String domain = "external-api.faa.gov";
        final String responseFormat = "geoJson";
        final String icaoLocation = "KOKC";   // TODO: replace with UserAirportInput later
        final String pageSize = "50";
        final String pageNum = "1";
        final String sortBy = "effectiveStartDate";
        final String sortOrder = "Desc";

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

        final HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("client_id", clientId)
                .header("client_secret", clientSecret)
                .build();

        final HttpClient http = HttpClient.newHttpClient();
        final HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

        System.out.println("URL: " + uri);
        System.out.println("Status: " + response.statusCode());
        System.out.println(response.body());
    }

    private static String enc(final String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}