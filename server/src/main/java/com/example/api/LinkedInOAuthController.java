package com.example.api;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.linkedin.oauth.builder.ScopeBuilder;
import com.linkedin.oauth.pojo.AccessToken;
import com.linkedin.oauth.service.LinkedInOAuthService;
import org.springframework.web.servlet.view.RedirectView;

import static com.example.api.Constants.TOKEN_INTROSPECTION_ERROR_MESSAGE;
import static com.example.api.Constants.LI_ME_ENDPOINT;
import static com.example.api.Constants.USER_AGENT_OAUTH_VALUE;
import static com.linkedin.oauth.util.Constants.TOKEN_INTROSPECTION_URL;
import static com.linkedin.oauth.util.Constants.REQUEST_TOKEN_URL;

/*
 * Getting Started with LinkedIn's OAuth APIs ,
 * Documentation: https://docs.microsoft.com/en-us/linkedin/shared/authentication/authentication?context=linkedin/context
 */

@RestController
public final class LinkedInOAuthController {

  @Autowired
  private RestTemplate restTemplate;

  // Define all inputs in the property file
  private Properties prop = new Properties();
  public static String token = null;
  public String refresh_token = null;
  public LinkedInOAuthService service;

  @Value("${clientId}")
  private String clientId;

  @Value("${clientSecret}")
  private String clientSecret;

  @Value("${redirectUri}")
  private String redirectUri;

  @Value("${scope}")
  private String scope;

  @Value("${client_url}")
  private String client_url;

  private Logger logger = Logger.getLogger(LinkedInOAuthController.class.getName());

  @RequestMapping("/logout")
  public RedirectView logout() {
    token = null;
    refresh_token = null;
    return new RedirectView(client_url);
  }

  // three legged oauth step 4  after user logs in through linkedin, they are redirected to
  // localhost:8080/login?code=123...
  /**
   * Make a Login request with LinkedIN Oauth API
   *
   * @param code optional Authorization code
   * @return Redirects to the client UI after successful token creation
   */
  @RequestMapping(value = "/login")
  public RedirectView oauth(@RequestParam(name = "code", required = false) final String code) throws Exception {

    loadProperty();
    // Construct the LinkedInOAuthService instance for use
    service = new LinkedInOAuthService.LinkedInOAuthServiceBuilder()
        .apiKey(
            prop.getProperty("clientId"))
        .apiSecret(prop.getProperty("clientSecret"))
        .defaultScope(new ScopeBuilder(prop.getProperty("scope").split(",")).build()) // replace with desired scope
        .callback(prop.getProperty("redirectUri"))
        .build();

    final String secretState = "secret" + new Random().nextInt(999_999);
    final String authorizationUrl = service.createAuthorizationUrlBuilder()
        .state(secretState)
        .build();

    RedirectView redirectView = new RedirectView();

    //
    if (code != null && !code.isEmpty()) {

      logger.log(Level.INFO, "Authorization code not empty, trying to generate a 3-legged OAuth token.");

      final AccessToken[] accessToken = {
          new AccessToken()
      };
      HttpEntity request = service.getAccessToken3Legged(code);
      // three legged oauth step 5 authorization code is exchanged for an access token from linkedin
      String response = restTemplate.postForObject(REQUEST_TOKEN_URL, request, String.class);
      accessToken[0] = service.convertJsonTokenToPojo(response);

      prop.setProperty("token", accessToken[0].getAccessToken());
      token = accessToken[0].getAccessToken();
      refresh_token = accessToken[0].getRefreshToken(); // null

      redirectView.setUrl(prop.getProperty("client_url"));
    } else {
      // three legged oauth step 3 user is redirected to LinkedIn login page
      // https://www.linkedin.com/uas/login?session_redirect...
      redirectView.setUrl(authorizationUrl);
    }
    return redirectView;
  }

  /**
   * Create 2 legged auth access token
   *
   * @return Redirects to the client UI after successful token creation
   */
  // doesn't work because linkedin api is broken
  @RequestMapping(value = "/twoLeggedAuth")
  public RedirectView two_legged_auth() throws Exception {
    loadProperty();

    RedirectView redirectView = new RedirectView();
    // Construct the LinkedInOAuthService instance for use
    service = new LinkedInOAuthService.LinkedInOAuthServiceBuilder()
        .apiKey(prop.getProperty("clientId"))
        .apiSecret(prop.getProperty("clientSecret"))
        .defaultScope(new ScopeBuilder(prop.getProperty("scope").split(",")).build())
        .callback(prop.getProperty("redirectUri"))
        .build();

    final AccessToken[] accessToken = {
        new AccessToken()
    };

    HttpEntity request = service.getAccessToken2Legged();
    // two legged auth step 2 get access token from linkedin already. 3 legged has a step before this which is getting the authorization code first. This doesn't work error: "message":"401 Unauthorized: [no body]","path":"/twoLeggedAuth"}"
    String response = restTemplate.postForObject(REQUEST_TOKEN_URL, request, String.class);
    accessToken[0] = service.convertJsonTokenToPojo(response);
    prop.setProperty("token", accessToken[0].getAccessToken());
    token = accessToken[0].getAccessToken();

    redirectView.setUrl(prop.getProperty("client_url"));
    return redirectView;
  }

  /**
   * Make a Token Introspection request with LinkedIN API
   *
   * @return check the Time to Live (TTL) and status (active/expired) for all
   *         token
   */
// three legged oauth step 2
@RequestMapping(value = "/tokenIntrospection")
public String token_introspection() throws Exception {
  if (service != null) {
    HttpEntity request = service.introspectToken(token);
    // three legged oauth step 8 POST request to linkedin's token introspection endpoint to get the
    // token details like expiry time, active, etc
    // token introspection step 2
      String response = restTemplate.postForObject(TOKEN_INTROSPECTION_URL, request, String.class);

      return response;
    } else {
      return TOKEN_INTROSPECTION_ERROR_MESSAGE;
    }
  }

  /**
   * Make a Refresh Token request with LinkedIN API
   *
   * @return get a new access token when your current access token expire
   */

  @RequestMapping(value = "/refreshToken")
  public String refresh_token() throws IOException {
    String response = null;
    if (refresh_token != null) {
      HttpEntity request = service.getAccessTokenFromRefreshToken(refresh_token);
      // Refresh token step 2 this doesn;t work because never got the refresh token when first logging in with three legged oauth
      response = restTemplate.postForObject(REQUEST_TOKEN_URL, request, String.class);
      logger.log(Level.INFO, "Used Refresh Token to generate a new access token successfully.");
      return response;
    } else {
      logger.log(Level.INFO, "Refresh Token cannot be empty. Generate 3L Access Token and Retry again.");
      return response;
    }
  }

  /**
   * Make a Public profile request with LinkedIN API
   *
   * @return Public profile of user
   */
  @RequestMapping(value = "/profile")
  public String profile() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.USER_AGENT, USER_AGENT_OAUTH_VALUE);
    // Get Profile step 3 this errors because linkedin api is broken. scope should be openid
    // but it's erroring, using scope profile instead but it doesn't have enough
    // permissions
    // Forbidden:
    // "{"status":403,"serviceErrorCode":100,"code":"ACCESS_DENIED","message":"Not
    // enough permissions to access: me.GET.NO_VERSION"}"]
    // response is supposed to be 
    return restTemplate.exchange(LI_ME_ENDPOINT + token, HttpMethod.GET, new HttpEntity<>(headers), String.class)
        .getBody();
    // return "profile page";
  }

  private void loadProperty() throws IOException {
    prop.setProperty("clientId", clientId);
    prop.setProperty("clientSecret", clientSecret);
    prop.setProperty("redirectUri", redirectUri);
    prop.setProperty("scope", scope);
    prop.setProperty("client_url", client_url);
  }
}
