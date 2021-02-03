package com.outbrain.BootCampOmlevi;

import com.outbrain.callmeclient.CallMeRequest;
import com.outbrain.callmeclient.CallMeResponse;
import com.outbrain.callmeclient.ICallMeService;
import com.outbrain.ob1k.concurrent.ComposableFuture;

public class CallMeProxyDummy implements ICallMeService {
    @Override
    public ComposableFuture<CallMeResponse> getClicks(CallMeRequest callMeRequest) {
        return null;
    }
}
