package io.sentry.samples.spring.boot;

import java.util.UUID;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class RestApi extends RouteBuilder {

  @Autowired PersonService personService;

  @Override
  public void configure() {
    CamelContext context = new DefaultCamelContext();

    restConfiguration()
        .contextPath("/camel")
        .port(8080)
        .enableCORS(true)
        .apiContextPath("/api-doc")
        .apiProperty("api.title", "Test REST API")
        .apiProperty("api.version", "v1")
        .apiContextRouteId("doc-api")
        .component("servlet")
        .bindingMode(RestBindingMode.json);

    rest("/api/")
        .id("api-route")
        .consumes("application/json")
        .post("/bean")
        .bindingMode(RestBindingMode.json_xml)
        .type(MyBean.class)
        .to("direct:remoteService");

    rest("/api/")
        .id("api-route")
        .consumes("application/json")
        .post("/bean2")
        .bindingMode(RestBindingMode.json_xml)
        .type(MyBean.class)
        .to("direct:insert");

    from("direct:remoteService")
        .routeId("direct-route")
        .tracing()
        .multicast()
        .parallelProcessing()
        .to("direct:insert", "direct:insert2")
        .log(">>> ${body.id}")
        .log(">>> ${body.name}")
        .transform()
        .simple("Hello ${in.body.name}")
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

    from("direct:insert")
        .process(
            new Processor() {
              public void process(Exchange xchg) throws Exception {
                // Take the Employee object from the exchange and create the insert query
                MyBean employee = xchg.getIn().getBody(MyBean.class);
                String query =
                    "INSERT INTO person(id,firstName, lastName)values('"
                        + employee.getId()
                        + "','"
                        + employee.getName()
                        + "','"
                        + UUID.randomUUID().toString()
                        + "')";
                // Set the insert query in body and call camel jdbc
                xchg.getIn().setBody(query);
              }
            })
        .to("jdbc:dataSource");

    from("direct:insert2")
        .process(
            new Processor() {
              public void process(Exchange xchg) throws Exception {
                // Take the Employee object from the exchange and create the insert query
                MyBean employee = xchg.getIn().getBody(MyBean.class);
                String query =
                    "INSERT INTO person(id,firstName, lastName)values('9999"
                        + employee.getId()
                        + "','"
                        + employee.getName()
                        + "2','"
                        + UUID.randomUUID().toString()
                        + "')";
                // Set the insert query in body and call camel jdbc
                xchg.getIn().setBody(query);
              }
            })
        .to("jdbc:dataSource");
  }
}
