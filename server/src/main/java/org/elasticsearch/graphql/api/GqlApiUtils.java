/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.graphql.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.jackson.Log4jJsonObjectMapper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.common.xcontent.javautil.JavaUtilXContent;
import org.elasticsearch.common.xcontent.javautil.JavaUtilXContentGenerator;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.graphql.api.fake.GqlApiFakeHttpChannel;
import org.elasticsearch.graphql.api.fake.GqlApiFakeHttpRequest;
import org.elasticsearch.graphql.api.fake.GqlApiFakeRestChannel;
import org.elasticsearch.rest.*;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class GqlApiUtils {
    private static final Logger logger = LogManager.getLogger(GqlApiUtils.class);

    public static XContentBuilder createJavaUtilBuilder() throws IOException  {
        BytesStreamOutput bos = new BytesStreamOutput();
        XContentBuilder builder = new XContentBuilder(JavaUtilXContent.javaUtilXContent, bos);
        return builder;
    }

    public static Object getJavaUtilBuilderResult(XContentBuilder builder) throws Exception {
        builder.close();
        JavaUtilXContentGenerator generator = (JavaUtilXContentGenerator) builder.generator();
        return generator.getResult();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(ToXContent response) throws Exception {
        XContentBuilder builder = createJavaUtilBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return (Map) getJavaUtilBuilderResult(builder);
    }

    public static Map<String, Object> toMapSafe(ToXContent response) {
//        logger.info("toMapSafe {}", response);
        try {
            return toMap(response);
        } catch (Exception e) {
            System.out.println("Exception " + e);
            return null;
        }
    }

    public static <Request extends ActionRequest, Response extends ActionResponse>
            CompletableFuture<Map<String, Object>> executeAction(NodeClient client,
                                                                 ActionType<Response> action,
                                                                 Request request) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<Map<String, Object>>();
        client.execute(action, request, new ActionListener<Response>() {
            @Override
            public void onResponse(ActionResponse response) {
                try {
                    if (response instanceof ToXContentObject) {
                        future.complete(toMap((ToXContentObject) response));
                    }
                    throw new Exception("Response does not implement ToXContentObject.");
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> executeRestHandler(NodeClient client,
                                                              BaseRestHandler handler,
                                                              RestRequest.Method method,
                                                              String uri) throws Exception {
        logger.info("executeRestHandler {} {} {}", handler.getName(), method, uri);
        CompletableFuture<T> promise = new CompletableFuture<T>();
        XContentBuilder builder = GqlApiUtils.createJavaUtilBuilder();

        GqlApiFakeHttpRequest internalHttpRequest = new GqlApiFakeHttpRequest(method, uri, BytesArray.EMPTY, new HashMap<>());
        GqlApiFakeHttpChannel internalHttpChannel = new GqlApiFakeHttpChannel(null);
        RestRequest innerRequest = RestRequest.request(NamedXContentRegistry.EMPTY, internalHttpRequest, internalHttpChannel);
        GqlApiFakeRestChannel internalRestChannel = new GqlApiFakeRestChannel(builder, innerRequest, promise);

        handler.handleRequest(innerRequest, internalRestChannel, client);

        return promise;
    }

    public static <T> ActionListener<T> futureToListener(CompletableFuture<T> promise) {
        return new ActionListener<T>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResponse(Object o) {
                promise.complete((T) o);
            }

            @Override
            public void onFailure(Exception e) {
                promise.completeExceptionally(e);
            }
        };
    }

    public static <T> Function<T, T> logResult(Logger logger, String message) {
        return res -> {
            logger.info("{} [value ~> {}]", message, res);
            return res;
        };
    }

    public static String getSomeMapKey(Map<String, Object> map) {
        Iterator<String> it = map.keySet().iterator();
        return it.hasNext() ? it.next() : null;
    }

    public static Map<String, Object> parseJson(String json) {
        return XContentHelper.convertToMap(JsonXContent.jsonXContent, json, false);
    }

    @SuppressWarnings("unchecked")
    public static String serializeJson(Object obj) throws Exception {
        XContentBuilder builder = JsonXContent.contentBuilder();

        if (obj instanceof Map) {
            return Strings.toString(builder.map((Map) obj));
        }

        return Strings.toString(builder.value(obj));
    }

    public static <I, O> Publisher<O> transformPublisher(Publisher<I> publisher, Function<I, O> fn) {
        return s -> {
            publisher.subscribe(new Subscriber<I>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    s.onSubscribe(subscription);
                }

                @Override
                public void onNext(I i) {
                    try {
                        s.onNext(fn.apply(i));
                    } catch (Exception e) {
                        s.onError(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    s.onError(t);
                }

                @Override
                public void onComplete() {
                    s.onComplete();
                }
            });
        };
    }
}
