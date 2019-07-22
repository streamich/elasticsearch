package org.elasticsearch.graphql.api;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.main.MainAction;
import org.elasticsearch.action.main.MainRequest;
import org.elasticsearch.action.main.MainResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.common.xcontent.json.JsonXContentGenerator;

import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GqlElasticsearchApi implements GqlApi {
    NodeClient client;

    public GqlElasticsearchApi(NodeClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Map<String, Object>> getHello() {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture();
        client.execute(MainAction.INSTANCE, new MainRequest(), new ActionListener<MainResponse>() {
            @Override
            public void onResponse(MainResponse mainResponse) {
                try {
                    XContentBuilder builder = mainResponse.toXContent(new XContentBuilder(JsonXContent.jsonXContent, OutputStream.nullOutputStream()), ToXContent.EMPTY_PARAMS);
                    future.complete(mainResponse.toMap());
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
}
