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

package org.elasticsearch.graphql.pubsub;

import org.reactivestreams.Subscriber;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PubSub {
    Map<String, List<Subscriber<Object>>> subscribers = new HashMap<String, List<Subscriber<Object>>>();

    public interface Subscription {
        void unsubscribe();
    }

    public Subscription subscribe(String channel, Subscriber<Object> subscriber) {
        if (!subscribers.containsKey(channel)) {
            final List<Subscriber<Object>> list = new LinkedList<Subscriber<Object>>();
            subscribers.put(channel, list);
            return subscribe0(list, channel, subscriber);
        } else {
            return subscribe0(subscribers.get(channel), channel, subscriber);
        }
    }

    private Subscription subscribe0(List<Subscriber<Object>> list, String channel, Subscriber<Object> subscriber) {
        Subscriber<Object> innerSubscriber = new Subscriber<Object>() {
            @Override
            public void onSubscribe(org.reactivestreams.Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(Object o) {
                subscriber.onNext(o);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                list.remove(this);
                if (list.isEmpty()) {
                    subscribers.remove(channel);
                }
                subscriber.onComplete();
            }
        };

        list.add(innerSubscriber);
        return () -> {
            list.remove(innerSubscriber);
            if (list.isEmpty()) {
                subscribers.remove(channel);
            }
        };
    }

    public void publish(String channel, Object message) {
        System.out.println("publishing " + channel + " "  + message);
        final List<Subscriber<Object>> list = subscribers.get(channel);
        if (list == null) return;
        for (Subscriber<Object> subscriber : list) {
            subscriber.onNext(message);
        }
    }
}
