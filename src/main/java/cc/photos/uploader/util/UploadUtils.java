package cc.photos.uploader.util;

import io.github.openunirest.http.HttpResponse;
import io.github.openunirest.http.JsonNode;
import io.github.openunirest.http.Unirest;

import org.apache.http.client.ConnectionBackoffStrategy;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.lang.String.format;

public class UploadUtils implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(UploadUtils.class);

  private static final int SC_TOO_MANY_REQUESTS = 429;
  private static final int SC_5XX = 500;
  private static final int SECONDS_TO_PAUSE_WHEN_TOO_MANY_REQUESTS = 30;

  private UploadUtils(HttpClient client) {
    Unirest.setHttpClient(client);
  }

  public static UploadUtils instance() {
     HttpClient client = HttpClientBuilder.create()
         .setRetryHandler(retryHandler())
         .setConnectionBackoffStrategy(connectionBackoffStrategy())
         .build();
     return new UploadUtils(client);
  }

  private static ConnectionBackoffStrategy connectionBackoffStrategy() {
    return new ConnectionBackoffStrategy() {
      @Override
      public boolean shouldBackoff(final Throwable t) {
        if(LOG.isInfoEnabled()) {
          LOG.info("Caught throwable to rule on backing off: {}", t.getMessage());
        }
        return (t instanceof SocketTimeoutException
            || t instanceof ConnectException);
      }

      @Override
      public boolean shouldBackoff(final org.apache.http.HttpResponse resp) {
        if(LOG.isInfoEnabled()) {
          LOG.info("Response status code to rule on backing off: {}", resp.getStatusLine());
        }
        // https://developers.google.com/photos/library/guides/best-practices#retrying-failed-requests
        return (resp.getStatusLine().getStatusCode() >= SC_5XX);
      }
    };
  }

  private static HttpRequestRetryHandler retryHandler() {
    return (exception, executionCount, context) -> {
      if (executionCount >= 10) {
        // Do not retry if over max retry count
        return false;
      }
      if (exception != null) {
        LOG.error("Caught error when attempting a retry.", exception);
        return false;
      }
      HttpClientContext clientContext = HttpClientContext.adapt(context);
      org.apache.http.HttpResponse response = clientContext.getResponse();
      if(response.getStatusLine().getStatusCode() == SC_TOO_MANY_REQUESTS) {
        // https://developers.google.com/photos/library/guides/best-practices#retrying-failed-requests
        if(LOG.isInfoEnabled()) {
          LOG.info("Too many requests sleeping for {} seconds ({})", SECONDS_TO_PAUSE_WHEN_TOO_MANY_REQUESTS, executionCount);
        }
        try {
          TimeUnit.SECONDS.sleep(SECONDS_TO_PAUSE_WHEN_TOO_MANY_REQUESTS);
        } catch (InterruptedException e) {
          // ignored
        }
        return true;
      } else {
        return false;
      }
    };
  }

  public JSONObject get(String url, Map<String, String> headers, ResponseHandler<JSONObject, JsonNode> handler) {
    if(LOG.isDebugEnabled()) {
      logRequest("GET", url, headers);
    }
    HttpResponse<JsonNode> response = Unirest.get(url).headers(headers).asJson();
    if(LOG.isDebugEnabled()) {
      logResponse(response, b->b.getObject().toString(2));
    }
    return handleResponse(response, handler);
  }

  public JSONObject post(String url, Map<String, String> headers, JSONObject body, ResponseHandler<JSONObject, JsonNode> handler) {
    if(LOG.isDebugEnabled()) {
      logRequest("POST", url, headers, body);
    }
    HttpResponse<JsonNode> response = Unirest.post(url).headers(headers).body(body).asJson();
    if(LOG.isDebugEnabled()) {
      logResponse(response, b->b.getObject().toString(2));
    }
    return handleResponse(response, handler);
  }

  public String post(String url, Map<String, String> headers, byte[] body, ResponseHandler<String, String> handler) {
    if(LOG.isDebugEnabled()) {
      logRequest("POST", url, headers);
    }
    HttpResponse<String> response = Unirest.post(url).headers(headers).body(body).asString();
    if(LOG.isDebugEnabled()) {
      logResponse(response, (s)->s);
    }
    return handleResponse(response, handler);
  }

  private <T,V> T handleResponse(HttpResponse<V> response, ResponseHandler<T, V> handler) {
    if(response.getStatus() >= 400) {
      throw new UploadException(response.getStatus(), response.getStatusText(), response.getBody().toString());
    }

    return handler.handleResponse(response);
  }

  private <T> void logResponse(HttpResponse<T> response, Function<T,String> renderBody) {
    LOG.debug("response: {} [{}]", response.getStatusText(), response.getStatus());
    LOG.debug("response headers:");
    response.getHeaders().forEach(
        (k,v) -> LOG.debug("    {}: {}", k, v)
    );
    LOG.debug("response body:");
    LOG.debug("\n>    {}", renderBody.apply(response.getBody()));
  }

  private void logRequest(String method, String url, Map<String, String> headers) {
    logRequest(method, url, headers, null);
  }

  private void logRequest(String method, String url, Map<String, String> headers, @Nullable JSONObject body) {
    LOG.debug("request: ");
    LOG.debug("    {} {}", method, url);
    LOG.debug("request headers: ");
    headers.forEach(
        (k,v) -> LOG.debug("    {}: {}", k, v)
    );
    if(body != null) {
      LOG.debug("request body: ");
      LOG.debug("\n{}", body.toString(2));
    }
    LOG.debug("-----------------------------------------------------");
  }

  public interface ResponseHandler<T, V> {
    T handleResponse(HttpResponse<V> response);
  }

  public static JSONObject defaultHandler(HttpResponse<JsonNode> response) {
    return response.getBody().getObject();
  }

  @Override
  public void close() {
    Unirest.shutdown();
  }

  public static KeyValue h(String key, String value) {
    return new KeyValue(key, value);
  }

  public static KeyValue acceptHeader(String type) {
    return new KeyValue("accept", type);
  }

  public static KeyValue contentTypeHeader(String type) {
    return new KeyValue("content-type", type);
  }

  public static Map<String, String> headers(String accessToken, KeyValue ... additionalHeaders) {
    Map<String, String> headers = new HashMap<>();
    headers.put("authorization", format("Bearer %s", accessToken));
    for(KeyValue kv : additionalHeaders) {
      headers.put(kv.getKey(), kv.getValue());
    }
    return headers;
  }
}

