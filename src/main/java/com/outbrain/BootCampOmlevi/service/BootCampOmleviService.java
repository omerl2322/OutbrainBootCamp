package com.outbrain.BootCampOmlevi.service;

import com.google.common.collect.Maps;
import com.outbrain.bootcampomleviclient.IBootCampOmleviClient;
import com.outbrain.bootcampomleviclient.model.MostClickedDocs;
import com.outbrain.callmeclient.CallMeRequest;
import com.outbrain.callmeclient.CallMeResponse;
import com.outbrain.callmeclient.Click;
import com.outbrain.callmeclient.ICallMeService;
import com.outbrain.event.client.EventClient;
import com.outbrain.metrics.MetricFactory;
import com.outbrain.ob1k.cache.LocalAsyncCache;
import com.outbrain.ob1k.concurrent.ComposableFuture;
import com.outbrain.ob1k.concurrent.ComposableFutures;
import com.outbrain.swinfra.metrics.Counter;
import com.outbrain.swinfra.metrics.Summary;
import com.outbrain.swinfra.metrics.timing.Timer;
import com.outbrain.tracing.api.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * BootCampOmlevi service entry point
 */
public class BootCampOmleviService implements IBootCampOmleviClient {
    private static final Logger logger = LoggerFactory.getLogger(BootCampOmleviService.class);
    private static final Pattern GEO_PATTERN = Pattern.compile("^[a-zA-Z]{2}$");
    private final String greetingMessage;
    private final ICallMeService callMeServiceProxy;
    private final LocalAsyncCache<String, MostClickedDocs> cachedClicks;
    private final Summary getDocsByGeoLatency;
    private final Counter cacheHitsCounter;
    private final Counter geoCalls;
    private static final Tracer tracer = Tracer.getTracer();


    public BootCampOmleviService(final String greetingMessage,
                                 final ICallMeService callMeServiceProxy,
                                 final LocalAsyncCache<String, MostClickedDocs> cachedClicks,
                                 final MetricFactory metricFactory) {
        this.greetingMessage = greetingMessage;
        this.callMeServiceProxy = callMeServiceProxy;
        this.cachedClicks = cachedClicks;
        this.getDocsByGeoLatency = metricFactory.newSummary("getDocsByGeoLatency",
                "Measure distribution of latency while calling a clickedDocsByGeo function");
        this.cacheHitsCounter = metricFactory.newCounter("cacheHitsCounter", "Measure the number of cache hits");
        this.geoCalls = metricFactory.newCounter("geoCalls", "Count calls for clickedDocsByGeo", "isValid");
    }


    public ComposableFuture<String> echo(final String name) {
        return ComposableFutures.fromValue("hello " + name + ". " + greetingMessage);

    }

    public ComposableFuture<MostClickedDocs> clickedDocsByGeo(final String geo) {
        return ComposableFutures.withTracing("clickedDocsByGeo-" + geo, () -> {
            Timer timer = getDocsByGeoLatency.startTimer();
            tracer.withTag("geo", geo);
            if (Objects.isNull(geo) || !(GEO_PATTERN.matcher(geo).matches())) {
                String errorMessage = "Invalid geo: " + geo + " ,geo should not be empty and must contain only characters.";
                timer.stop();
                logger.warn(errorMessage);
                geoCalls.inc("false");
                return ComposableFutures.fromError(new IllegalArgumentException(errorMessage));
            }
            geoCalls.inc("true");
            return cachedClicks.getAsync(geo)
                    .recover(err -> {
                        logger.error("Failed to get cached response: {}", err.getMessage());
                        timer.stop();
                        return null;
                    })
                    .flatMap(cachedResult -> {
                        if (cachedResult != null) {
                            cacheHitsCounter.inc();
                            logger.info("Getting docs for geo: {} from cache", geo);
                            return ComposableFutures.fromValue(cachedResult);
                        }
                        return getClicksFromCallMeService(geo).flatMap(result ->
                                cachedClicks.setAsync(geo, result).map(saved -> result));
                    }).andThen(aTry -> timer.stop())
                    .peek(r -> tracer.log("response", r::toString));
        });

    }

    private ComposableFuture<MostClickedDocs> getClicksFromCallMeService(final String geo) {
        return getMostClickDocs(geo)
                .recoverWith(e -> {
                    logger.error("calling CallMeService getClicks() failed: [{}]", e.getMessage(), e);
                    return ComposableFutures.fromError(e);
                })
                .map(callMeResponse -> {
                    logger.info("Getting docs for geo: {} from service", geo);
                    return createMostClickedDocs(geo, callMeResponse);
                });
    }

    private MostClickedDocs createMostClickedDocs(final String geo, final CallMeResponse callMeResponse) {
        final Map<String, Long> urlClickMap = callMeResponse.getClicks().stream().
                collect(groupingBy(Click::getUrl, counting()));
        final long sumNumOfClicks = urlClickMap.values().stream().mapToLong(Long::longValue).sum();
        sendClickedDocsByGeoEvent(geo, sumNumOfClicks);
        final List<String> urlClicked = urlClickMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .map(Map.Entry::getKey).collect(Collectors.toList());
        return new MostClickedDocs(geo, urlClicked);
    }

    private ComposableFuture<CallMeResponse> getMostClickDocs(final String geo) {
        final CallMeRequest request = createRequest(geo);
        return callMeServiceProxy.getClicks(request);
    }

    private CallMeRequest createRequest(final String geo) {
        final CallMeRequest request = new CallMeRequest();
        request.setGeo(geo);
        request.setCaller("BootCampOmleviService");
        return request;
    }

    private void sendClickedDocsByGeoEvent(final String geo, final long numOfClicks) {
        final EventClient eventClient = EventClient.getInstance();
        final Map<String, Object> map = Maps.newHashMap();
        map.put("bootcamp_owner", "omlevi");
        map.put("bootcamp_geo", geo);
        map.put("bootcamp_clicks", numOfClicks); // The sum of clicks from all documents in this geo
        map.put("document_id", "put your name here" + System.currentTimeMillis());
        eventClient.sendEvent("BootCampOmleviService", "appinfra", map);
    }
}
