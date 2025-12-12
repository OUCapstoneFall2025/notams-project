package ou.capstone.notams.api;

public class ApiCredentials {
    private final String clientId;
    private final String clientSecret;

    public ApiCredentials(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }
}