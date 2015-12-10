package nl.revolution.oauth2.example;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Properties;

public class RequestHandler extends AbstractHandler {

    private static final String OAUTH2_CALLBACK_PATH = "/oauth2callback";
    private static final String GITHUB_OAUTH_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_OAUTH_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_API_USER_DETAILS_URL = "https://api.github.com/user?access_token=";
    private static final Logger LOG = LoggerFactory.getLogger(RequestHandler.class);
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
    private static final String PROPERTY_FILE_PATH = "/Users/bertjan/.github.properties";

    private AuthorizationCodeFlow authorizationCodeFlow;
    private String githubClientId;
    private String githubClientSecret;

    public RequestHandler() {
        // Reads Github id/secret from property file. Create this file and enter your own credentials.
        Properties properties = loadProperties();
        githubClientId = properties.getProperty("github.client.id");
        githubClientSecret = properties.getProperty("github.client.secret");
        if (githubClientId == null || githubClientSecret == null) {
            LOG.error("Github client id/secret not configured - enter in " + PROPERTY_FILE_PATH + ".");
        }

        authorizationCodeFlow = initializeAuthorizationCodeFlow();
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        final String requestURI = request.getRequestURI();

        // Landing page.
        if (requestURI.equals("/")) {
            String body = "<html><body><a href=\"/login\">login</a></body></html>";
            htmlResponse(baseRequest, response, body);
            return;
        }

        // Login page, redirects to OAuth provider.
        if (requestURI.equals("/login")) {
            sendRedirectToOAuthProvider(baseRequest, request, response);
            return;
        }

        // Callback URL, handles response from OAuth provider.
        if (requestURI.equals(OAUTH2_CALLBACK_PATH)) {
            String oauthCode = processOAuthCodeCallback(baseRequest, request);
            if (oauthCode == null) {
                errorResponseForLogin(baseRequest, response);
                return;
            }

            TokenResponse tokenResponse = performOAuthTokenAuthentication(request, oauthCode);
            if (tokenResponse == null) {
                errorResponseForLogin(baseRequest, response);
                return;
            }

            JSONObject userDetails = fetchUserDetails(tokenResponse);
            if (userDetails == null) {
                errorResponseForLogin(baseRequest, response);
            }

            LOG.debug("User details received.");
            plainTextResponse(baseRequest, response, "Welcome " + userDetails.get("name") + ", you are now logged in.");
            return;
        }

        // No matching route found.
        LOG.info("Could not match a request for requestURI {}, responding with 404.", requestURI);
        new NotFoundHandler().handle(target, baseRequest, request, response);
    }



    private void sendRedirectToOAuthProvider(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        baseRequest.setHandled(true);
        AuthorizationCodeRequestUrl authorizationUrl = authorizationCodeFlow.newAuthorizationUrl();
        GenericUrl url = new GenericUrl(request.getRequestURL().toString());
        url.setRawPath("/oauth2callback");
        String redirectURL = authorizationUrl.setRedirectUri(url.build()).build();
        LOG.debug("Sending redirect to " + redirectURL);
        response.sendRedirect(redirectURL);
    }


    private String processOAuthCodeCallback(Request baseRequest, HttpServletRequest request) throws IOException {
        baseRequest.setHandled(true);

        String urlWithQueryString = request.getRequestURL() + "?" + request.getQueryString();
        AuthorizationCodeResponseUrl responseUrl = new AuthorizationCodeResponseUrl(urlWithQueryString);
        String code = responseUrl.getCode();
        if (responseUrl.getError() != null || code == null) {
            return null;
        }

        // Authentication code received.
        LOG.debug("Received authentication code.");

        return code;
    }


    private TokenResponse performOAuthTokenAuthentication(HttpServletRequest request, String oauthCode) throws IOException {
        // Send token request.
        GenericUrl url = new GenericUrl(request.getRequestURL().toString());
        url.setRawPath(OAUTH2_CALLBACK_PATH);
        String redirectUri = url.build();
        AuthorizationCodeTokenRequest tokenRequest = authorizationCodeFlow.newTokenRequest(oauthCode).setRedirectUri(redirectUri);
        // Make sure that we ask for a JSON response.
        tokenRequest.setRequestInitializer(httpRequest -> httpRequest.getHeaders().setAccept("application/json"));

        LOG.debug("Sending token request.");
        TokenResponse tokenResponse = tokenRequest.executeUnparsed().parseAs(TokenResponse.class);
        if (tokenResponse.get("error") != null) {
            LOG.error("Error in token response: {}", tokenResponse.get("error"));
            LOG.error("Details: {}", tokenResponse.toPrettyString());
            return null;
        }
        return tokenResponse;
    }


    private JSONObject fetchUserDetails(TokenResponse tokenResponse) throws IOException {
        // Access token received.
        // Fetch user details.
        LOG.debug("Access token received. Fetching user details.");
        String userDetailsStr = performHTTPGet(GITHUB_API_USER_DETAILS_URL + tokenResponse.getAccessToken());
        JSONObject userDetails = parseJson(userDetailsStr);
        if (userDetails == null) {
            return null;
        }
        return userDetails;
    }


    protected AuthorizationCodeFlow initializeAuthorizationCodeFlow() {
        try {
            return new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(),
                    new NetHttpTransport(),
                    new JacksonFactory(),
                    new GenericUrl(GITHUB_OAUTH_ACCESS_TOKEN_URL),
                    new BasicAuthentication(githubClientId, githubClientSecret),
                    githubClientId,
                    GITHUB_OAUTH_AUTHORIZE_URL).setCredentialDataStore(
                    MemoryDataStoreFactory.getDefaultInstance().getDataStore("credentialDatastore"))
                    .build();
        } catch (IOException e) {
            LOG.error("Error initializing authorization code flow: ", e);
            return null;
        }
    }

    private void writeResponse(Request baseRequest, HttpServletResponse response, int responseCode, String contentType, String body) throws IOException {
        response.setContentType(contentType);
        response.setStatus(responseCode);
        baseRequest.setHandled(true);
        OutputStream out = response.getOutputStream();
        out.write(body.getBytes(CHARSET_UTF_8));
        out.flush();
        out.close();
    }

    private void plainTextResponse(Request baseRequest, HttpServletResponse response, String body) throws IOException {
        writeResponse(baseRequest, response, HttpServletResponse.SC_OK, "text/plain", body);
        return;
    }

    private void htmlResponse(Request baseRequest, HttpServletResponse response, String body) throws IOException {
        writeResponse(baseRequest, response, HttpServletResponse.SC_OK, "text/html", body);
        return;
    }


    private void errorResponseForLogin(Request baseRequest, HttpServletResponse response) throws IOException {
        writeResponse(baseRequest, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "text/plain", "Login failed.");
        return;
    }

    private String performHTTPGet(String url) throws IOException {
        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
        GenericUrl getUserDataUrl = new GenericUrl(url);
        HttpRequest userDetailsRequest = requestFactory.buildGetRequest(getUserDataUrl);
        HttpResponse userDetailsResponse = userDetailsRequest.execute();
        return userDetailsResponse.parseAsString();
    }

    private JSONObject parseJson(String input) {
        try {
            return (JSONObject) new JSONParser().parse(input);
        } catch (ParseException e) {
            LOG.error("Error while parsing JSON: ", e);
            return null;
        }
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(PROPERTY_FILE_PATH));
        } catch (IOException e) {
            LOG.error("Error loading properties: ", e);
        }
        return properties;
    }

}
