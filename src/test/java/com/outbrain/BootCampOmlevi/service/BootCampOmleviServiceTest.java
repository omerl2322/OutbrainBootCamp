package com.outbrain.BootCampOmlevi.service;

import com.outbrain.bootcampomleviclient.model.MostClickedDocs;
import com.outbrain.callmeclient.CallMeProxy;
import com.outbrain.callmeclient.CallMeResponse;
import com.outbrain.callmeclient.Click;
import com.outbrain.ob1k.cache.LocalAsyncCache;
import com.outbrain.ob1k.concurrent.ComposableFuture;
import com.outbrain.ob1k.concurrent.ComposableFutures;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import static org.junit.Assert.fail;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import com.outbrain.metrics.MetricFactory;
import com.outbrain.swinfra.metrics.Summary;
import com.outbrain.swinfra.metrics.timing.Timer;
import com.outbrain.swinfra.metrics.Counter;

@RunWith(MockitoJUnitRunner.class)
public class BootCampOmleviServiceTest {

    private final static String GREETING_MSG = "GREETING_MSG";
    private final CallMeProxy callMeMock = Mockito.mock(CallMeProxy.class);
    private final LocalAsyncCache<String, MostClickedDocs> cachedClicks = Mockito.mock(LocalAsyncCache.class);
    private BootCampOmleviService service;
    private CallMeResponse callMeResponse;

    @Mock
    private MetricFactory metricFactory;
    @Mock
    private Timer timer;
    @Mock
    private Counter cacheHitsCounter;
    @Mock
    private Counter geoCalls;
    @Mock
    private Summary getDocsByGeoLatency;


    @Before
    public void setup() {
        Click testClick = new Click(1, 2, "msn.com");
        List<Click> clicks = new ArrayList<>();
        clicks.add(testClick);
        callMeResponse = new CallMeResponse(clicks);
        Mockito.when(metricFactory.newCounter("geoCalls", "Count calls for clickedDocsByGeo", "isValid")).thenReturn(geoCalls);
        Mockito.when(metricFactory.newCounter("cacheHitsCounter", "Measure the number of cache hits")).thenReturn(cacheHitsCounter);
        Mockito.when(metricFactory.newSummary(Mockito.anyString(), Mockito.anyString())).thenReturn(getDocsByGeoLatency);
        Mockito.when(getDocsByGeoLatency.startTimer()).thenReturn(timer);
        service = new BootCampOmleviService(GREETING_MSG, callMeMock, cachedClicks, metricFactory);
    }

    @Test
    public void testEchoResponse() throws ExecutionException, InterruptedException {
        String name = "ob1k";
        String response = service.echo(name).get();
        Assert.assertTrue(response.contains(name));
        Assert.assertTrue(response.contains(GREETING_MSG));
    }

    @Test
    public void testCallMeServiceResponse() throws ExecutionException, InterruptedException {
        final String geo = "BR";
        Mockito.when(cachedClicks.getAsync(geo)).thenReturn(ComposableFutures.fromNull());
        Mockito.when(callMeMock.getClicks(Mockito.any())).thenReturn(ComposableFutures.fromValue(callMeResponse));
        Mockito.when(cachedClicks.setAsync(Mockito.anyString(), Mockito.any(MostClickedDocs.class))).thenReturn(ComposableFutures.fromValue(true));
        ComposableFuture<MostClickedDocs> mostClickedDocsComposableFuture = service.clickedDocsByGeo(geo);
        Assert.assertTrue(mostClickedDocsComposableFuture.get().getClickedDocs().contains("msn.com"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClickedDocsByGeoInput(){
        final String geoTest = null;
        ComposableFuture<MostClickedDocs> response = service.clickedDocsByGeo(geoTest);
        response.getUnchecked();
    }
}
