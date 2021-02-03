package com.outbrain.BootCampOmlevi;

import com.outbrain.BootCampOmlevi.service.BootCampOmleviService;
import com.outbrain.ob1k.server.Server;
import com.outbrain.ob1k.server.builder.OBServerBuilder;
import com.outbrain.ob1k.server.builder.OBSpringContextBuilder;
import com.outbrain.ob1k.server.spring.SpringBeanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BootCampOmlevi server main entry point
 */
public class BootCampOmleviServer {
  private static final Logger logger = LoggerFactory.getLogger(BootCampOmleviServer.class);

  public static void main(final String[] args) {
    buildServer(true).start();
    logger.info("******** BootCampOmleviServer started ********");
  }

  static Server buildServer(final boolean useConfigurationPort) {
    final String contextPath = "/BootCampOmlevi";

    final SpringBeanContext ctx = new OBSpringContextBuilder(contextPath)
        .setMainContext("main", "classpath:applicationContext-BootCampOmlevi-all.xml")
        .addOpsContext("classpath:ops-services.xml")
        .addSubContext("service", "classpath:applicationContext-BootCampOmlevi-service.xml")
        .build();

    return OBServerBuilder.newBuilder(ctx, contextPath)
        .configure(builder -> builder.useConfigurationPort(useConfigurationPort))
        .service(builder -> builder.register("service", BootCampOmleviService.class, "/api")
        /*
            Register more services here inside the 'service' method.

                  Should look something like:

                            register("service", AnotherService.class, "/another").

                            register("service", YetAnotherService.class, "/yetAnother", bind ->
                                    bind.endpoint(HttpRequestMethodType.GET, "get", "/get").
                                          endpoint(....

                  NOTE: ALL services get the MetricsTimerFilter and HitsCounterFilter by default.

                  If you want to not have them registered to the service you can remove them for example:

                     register("service", AnotherService.class, "/another").withoutFilters(MetricsTimerFilter.class, HitsCounterFilter.class)

                  and obviously you can choose to remove only one of them. For example:

                     register("service", AnotherService.class, "/another").withoutFilters(HitsCounterFilter.class)

            Map Resources by calling the builder 'resource' method

                  Should look something like:

                    resource(builder -> builder.staticPath("/html").
                            staticPath("/css").
                            staticPath("/js").
                            staticPath("/img").
                            staticResource("/webjars", "/META-INF/resources/webjars")).


            Configure most stuff by either using the previous configure method or calling a new one.

                  Should look something like:

                  configure(builder -> builder.acceptKeepAlive(true).maxContentLength(MAX_CONTENT_LENGTH).supportZip(false)... etc. ).

            Its a builder! - use chaining of methods to make your code more concise and clean.
            You only need a ';' after calling build()....

            oh, and don't forget to call build (or the compiler will remind you)

         */
        ).build();
  }
}
