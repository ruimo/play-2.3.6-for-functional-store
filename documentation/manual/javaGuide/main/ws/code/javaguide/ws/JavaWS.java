/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package javaguide.ws;

import javaguide.testhelpers.MockJavaAction;

// #ws-imports
import play.libs.ws.*;
import play.libs.F.Function;
import play.libs.F.Promise;
// #ws-imports

// #json-imports
import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;
// #json-imports

import java.io.*;
import org.w3c.dom.Document;
import play.mvc.Result;

// #ws-custom-client-imports
import com.ning.http.client.*;
import play.api.libs.ws.WSClientConfig;
import play.api.libs.ws.DefaultWSClientConfig;
import play.api.libs.ws.ssl.SSLConfig;
import play.api.libs.ws.ning.NingAsyncHttpClientConfigBuilder;
// #ws-custom-client-imports

public class JavaWS {
    private static final String feedUrl = "http://localhost:3333/feed";

    public static class Controller0 extends MockJavaAction {

        public static void requestExamples() {
            // #ws-holder
            WSRequestHolder holder = WS.url("http://example.com");
            // #ws-holder

            // #ws-complex-holder
            WSRequestHolder complexHolder = holder.setHeader("headerKey", "headerValue")
                    .setTimeout(1000)
                    .setQueryParameter("paramKey", "paramValue");
            // #ws-complex-holder

            // #ws-get
            Promise<WSResponse> responsePromise = complexHolder.get();
            // #ws-get

            String url = "http://example.com";
            // #ws-auth
            WS.url(url).setAuth("user", "password", WSAuthScheme.BASIC).get();
            // #ws-auth

            // #ws-follow-redirects
            WS.url(url).setFollowRedirects(true).get();
            // #ws-follow-redirects

            // #ws-query-parameter
            WS.url(url).setQueryParameter("paramKey", "paramValue");
            // #ws-query-parameter

            // #ws-header
            WS.url(url).setHeader("headerKey", "headerValue").get();
            // #ws-header

            String jsonString = "{\"key1\":\"value1\"}";
            // #ws-header-content-type
            WS.url(url).setHeader("Content-Type", "application/json").post(jsonString);
            // OR
            WS.url(url).setContentType("application/json").post(jsonString);
            // #ws-header-content-type

            // #ws-timeout
            WS.url(url).setTimeout(1000).get();
            // #ws-timeout

            // #ws-post-form-data
            WS.url(url).setContentType("application/x-www-form-urlencoded")
                    .post("key1=value1&key2=value2");
            // #ws-post-form-data

            // #ws-post-json
            JsonNode json = Json.newObject()
                    .put("key1", "value1")
                    .put("key2", "value2");

            WS.url(url).post(json);
            // #ws-post-json
        }

        public static void responseExamples() {

            String url = "http://example.com";

            // #ws-response-json
            Promise<JsonNode> jsonPromise = WS.url(url).get().map(
                    new Function<WSResponse, JsonNode>() {
                        public JsonNode apply(WSResponse response) {
                            JsonNode json = response.asJson();
                            return json;
                        }
                    }
            );
            // #ws-response-json

            // #ws-response-xml
            Promise<Document> documentPromise = WS.url(url).get().map(
                    new Function<WSResponse, Document>() {
                        public Document apply(WSResponse response) {
                            Document xml = response.asXml();
                            return xml;
                        }
                    }
            );
            // #ws-response-xml

            // #ws-response-input-stream
            final Promise<File> filePromise = WS.url(url).get().map(
                    new Function<WSResponse, File>() {
                        public File apply(WSResponse response) throws Throwable {

                            InputStream inputStream = null;
                            OutputStream outputStream = null;
                            try {
                                inputStream = response.getBodyAsStream();

                                // write the inputStream to a File
                                final File file = new File("/tmp/response.txt");
                                outputStream = new FileOutputStream(file);

                                int read = 0;
                                byte[] buffer = new byte[1024];

                                while ((read = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, read);
                                }

                                return file;
                            } catch (IOException e) {
                                throw e;
                            } finally {
                                if (inputStream != null) {inputStream.close();}
                                if (outputStream != null) {outputStream.close();}
                            }

                        }
                    }
            );
            // #ws-response-input-stream
        }

        public static void patternExamples() {
            String urlOne = "http://localhost:3333/one";
            // #ws-composition
            final Promise<WSResponse> responseThreePromise = WS.url(urlOne).get().flatMap(
                    new Function<WSResponse, Promise<WSResponse>>() {
                        public Promise<WSResponse> apply(WSResponse responseOne) {
                            String urlTwo = responseOne.getBody();
                            return WS.url(urlTwo).get().flatMap(
                                    new Function<WSResponse, Promise<WSResponse>>() {
                                        public Promise<WSResponse> apply(WSResponse responseTwo) {
                                            String urlThree = responseTwo.getBody();
                                            return WS.url(urlThree).get();
                                        }
                                    }
                            );
                        }
                    }
            );
            // #ws-composition

            // #ws-recover
            Promise<WSResponse> responsePromise = WS.url("http://example.com").get();
            Promise<WSResponse> recoverPromise = responsePromise.recoverWith(new Function<Throwable, Promise<WSResponse>>() {
                @Override
                public Promise<WSResponse> apply(Throwable throwable) throws Throwable {
                    return WS.url("http://backup.example.com").get();
                }
            });
            // #ws-recover
        }

        public static void clientExamples() {
            // #ws-client
            WSClient client = WS.client();
            // #ws-client

            // #ws-custom-client
            // Set up the client config (you can also use a parser here):
            scala.Option<Object> none = scala.None$.empty();
            scala.Option<String> noneString = scala.None$.empty();
            scala.Option<SSLConfig> noneSSLConfig = scala.None$.empty();
            WSClientConfig clientConfig = new DefaultWSClientConfig(
                    none, // connectionTimeout
                    none, // idleTimeout
                    none, // requestTimeout
                    none, // followRedirects
                    none, // useProxyProperties
                    noneString, // userAgent
                    none, // compressionEnabled
                    none, // acceptAnyCertificate
                    noneSSLConfig);

            // Build a secure config out of the client config and the ning builder:
            AsyncHttpClientConfig.Builder asyncHttpClientBuilder = new AsyncHttpClientConfig.Builder();
            NingAsyncHttpClientConfigBuilder secureBuilder = new NingAsyncHttpClientConfigBuilder(clientConfig,
                    asyncHttpClientBuilder);
            AsyncHttpClientConfig secureDefaults = secureBuilder.build();

            // You can directly use the builder for specific options once you have secure TLS defaults...
           AsyncHttpClientConfig customConfig = new AsyncHttpClientConfig.Builder(secureDefaults)
                            .setProxyServer(new com.ning.http.client.ProxyServer("127.0.0.1", 38080))
                            .setCompressionEnabled(true)
                            .build();
            WSClient customClient = new play.libs.ws.ning.NingWSClient(customConfig);

            Promise<WSResponse> responsePromise = customClient.url("http://example.com/feed").get();
            // #ws-custom-client

            // #ws-underlying-client
            com.ning.http.client.AsyncHttpClient underlyingClient =
                    (com.ning.http.client.AsyncHttpClient) WS.client().getUnderlying();
            // #ws-underlying-client

        }
    }

    public static class Controller1 extends MockJavaAction {

        // #ws-action
        public static Promise<Result> index() {
            final Promise<Result> resultPromise = WS.url(feedUrl).get().map(
                    new Function<WSResponse, Result>() {
                        public Result apply(WSResponse response) {
                            return ok("Feed title:" + response.asJson().findPath("title"));
                        }
                    }
            );
            return resultPromise;
        }
        // #ws-action
    }

    public static class Controller2 extends MockJavaAction {

        // #composed-call
        public static Promise<Result> index() {
            final Promise<Result> resultPromise = WS.url(feedUrl).get().flatMap(
                    new Function<WSResponse, Promise<Result>>() {
                        public Promise<Result> apply(WSResponse response) {
                            return WS.url(response.asJson().findPath("commentsUrl").asText()).get().map(
                                    new Function<WSResponse, Result>() {
                                        public Result apply(WSResponse response) {
                                            return ok("Number of comments: " + response.asJson().findPath("count").asInt());
                                        }
                                    }
                            );
                        }
                    }
            );
            return resultPromise;
        }
        // #composed-call
    }

}
