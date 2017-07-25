package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CamelRouteManager implements CamelContextAware {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    protected CamelContext camelContext;
    
    @Autowired
    private SiriHelper siriHelper;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }


    
    
    /**
     * Creates a new ad-hoc route that sends the SIRI payload to supplied address, executes it, and finally terminates and removes it.
     * @param payload
     * @param subscriptionRequest
     */
    public void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest) {
        String consumerAddress = subscriptionRequest.getAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }

        
        Siri filteredPayload = siriHelper.filterSiriPayload(payload, subscriptionRequest.getFilterMap());

        // Use original/mapped ids based on subscription
        filteredPayload = SiriValueTransformer.transform(filteredPayload, subscriptionRequest.getValueAdapters());

        List<Siri> splitSiri = siriHelper.splitDeliveries(filteredPayload, 1000);

        logger.info("Object split into {} deliveries.", splitSiri.size());

        for (Siri siri : splitSiri) {
            Thread r = new Thread() {
                String routeId = "";
                @Override
                public void run() {
                    try {

                        SiriPushRouteBuilder siriPushRouteBuilder = new SiriPushRouteBuilder(consumerAddress, subscriptionRequest);
                        routeId = addSiriPushRoute(siriPushRouteBuilder);
                        executeSiriPushRoute(siri, siriPushRouteBuilder.getRouteName());
                    } catch (Exception e) {
                        if (e.getCause() instanceof SocketException) {
                            logger.info("Recipient is unreachable - ignoring");
                        } else {
                            logger.warn("Exception caught when pushing SIRI-data", e);
                        }
                    } finally {
                        try {
                            stopAndRemoveSiriPushRoute(routeId);
                        } catch (Exception e) {
                            logger.warn("Exception caught when removing route " + routeId, e);
                        }
                    }
                }
            };
            r.start();
        }
    }

    private String addSiriPushRoute(SiriPushRouteBuilder route) throws Exception {
        camelContext.addRoutes(route);
        logger.trace("Route added - CamelContext now has {} routes", camelContext.getRoutes().size());
        return route.getDefinition().getId();
    }

    private boolean stopAndRemoveSiriPushRoute(String routeId) throws Exception {
        int timeout = 30000;
        if (!camelContext.stopRoute(routeId, timeout, TimeUnit.MILLISECONDS, true)) {
            logger.warn("Route {} could not be stopped - aborted after {} ms", routeId, timeout);
        }
        if (!camelContext.removeRoute(routeId)) {
            logger.warn("Route {} could not be removed.");
        }
        logger.trace("Route removed - CamelContext now has {} routes", camelContext.getRoutes().size());
        return true;
    }


    private void executeSiriPushRoute(Siri payload, String routeName) throws JAXBException {
        if (!serviceDeliveryContainsData(payload)) {
            return;
        }
        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody(routeName, payload);
    }

    private boolean serviceDeliveryContainsData(Siri payload) {
        if (payload.getServiceDelivery() != null) {
            ServiceDelivery serviceDelivery = payload.getServiceDelivery();

            if (siriHelper.containsValues(serviceDelivery.getSituationExchangeDeliveries())) {
                SituationExchangeDeliveryStructure deliveryStructure = serviceDelivery.getSituationExchangeDeliveries().get(0);
                boolean containsSXdata = deliveryStructure.getSituations() != null &&
                        siriHelper.containsValues(deliveryStructure.getSituations().getPtSituationElements());
                logger.info("Contains SX-data: [{}]", containsSXdata);
                return containsSXdata;
            }

            if (siriHelper.containsValues(serviceDelivery.getVehicleMonitoringDeliveries())) {
                VehicleMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getVehicleMonitoringDeliveries().get(0);
                boolean containsVMdata = deliveryStructure.getVehicleActivities() != null &&
                        siriHelper.containsValues(deliveryStructure.getVehicleActivities());
                logger.info("Contains VM-data: [{}]", containsVMdata);
                return containsVMdata;
            }

            if (siriHelper.containsValues(serviceDelivery.getEstimatedTimetableDeliveries())) {
                EstimatedTimetableDeliveryStructure deliveryStructure = serviceDelivery.getEstimatedTimetableDeliveries().get(0);
                boolean containsETdata = (deliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        siriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames()) &&
                        siriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies()));
                logger.info("Contains ET-data: [{}]", containsETdata);
                return containsETdata;
            }
        }
        return true;
    }

    private class SiriPushRouteBuilder extends RouteBuilder {

        private final OutboundSubscriptionSetup subscriptionRequest;
        private String remoteEndPoint;
        private RouteDefinition definition;
        private String routeName;

        public SiriPushRouteBuilder(String remoteEndPoint, OutboundSubscriptionSetup subscriptionRequest) {
            this.remoteEndPoint=remoteEndPoint;
            this.subscriptionRequest = subscriptionRequest;
        }

        @Override
        public void configure() throws Exception {

            boolean isActiveMQ = false;

            if (remoteEndPoint.startsWith("http://")) {
                //Translating URL to camel-format
                remoteEndPoint = "http4://" + remoteEndPoint.substring("http://".length());
            } else if (remoteEndPoint.startsWith("https://")) {
                //Translating URL to camel-format
                remoteEndPoint = "https4://" + remoteEndPoint.substring("https://".length());
            } else if (remoteEndPoint.startsWith("activemq:")) {
                isActiveMQ = true;
            }

            routeName = String.format("direct:%s", UUID.randomUUID().toString());

            String options;
            if (isActiveMQ) {
                int timeout = subscriptionRequest.getTimeToLive();
                options = "?asyncConsumer=true&timeToLive=" + timeout;
            } else {
                int timeout = 60000;
                options = "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;
                onException(ConnectException.class)
                        .maximumRedeliveries(0)
                        .log("Failed to connect to recipient");

                errorHandler(noErrorHandler());
            }

            if (isActiveMQ) {
                definition = from(routeName)
                        .routeId(routeName)
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                        .to(ExchangePattern.InOnly, remoteEndPoint + options)
                        .log(LoggingLevel.INFO, "Pushed data to ActiveMQ-topic: [" + remoteEndPoint + "]");
            } else {
                definition = from(routeName)
                        .routeId(routeName)
                        .log(LoggingLevel.INFO, "POST data to " + remoteEndPoint + " [" + subscriptionRequest.getSubscriptionId() + "]")
                        .setHeader("SubscriptionId", constant(subscriptionRequest.getSubscriptionId()))
                        .setHeader("CamelHttpMethod", constant("POST"))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/xml"))
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .to(remoteEndPoint + options)
                        .to("log:push-resp:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .log(LoggingLevel.INFO, "POST complete [" + subscriptionRequest.getSubscriptionId() + "]");
            }

        }

        public RouteDefinition getDefinition() {
            return definition;
        }

        public String getRouteName() {
            return routeName;
        }
    }
}
