/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.siri;

import java.util.Map;
import java.util.Set;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.OperatorRefStructure;
import uk.org.siri.siri21.EstimatedTimetableRequestStructure;
import uk.org.siri.siri21.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeRequestStructure;
import uk.org.siri.siri21.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri21.VehicleMonitoringRequestStructure;
import uk.org.siri.siri21.VehicleMonitoringSubscriptionStructure;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SiriObjectFactoryTest {

    private final int hoursUntilInitialTermination = 1;

    @Test
    public void testCreateVMSubscription(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SiriDataType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri vmSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
        assertNotNull(vmSubscriptionRequest.getSubscriptionRequest());
        List<VehicleMonitoringSubscriptionStructure> subscriptionRequests = vmSubscriptionRequest.getSubscriptionRequest().getVehicleMonitoringSubscriptionRequests();
        assertNotNull(subscriptionRequests);

        assertTrue(subscriptionRequests.size() == 1);

        VehicleMonitoringSubscriptionStructure subscription = subscriptionRequests.get(0);
        assertNotNull(subscription.getSubscriptionIdentifier());
        assertNotNull(subscription.getSubscriptionIdentifier().getValue());
        assertEquals(subscriptionSetup.getSubscriptionId(), subscription.getSubscriptionIdentifier().getValue());

        ZonedDateTime initialTerminationTime = subscription.getInitialTerminationTime();

        assertTrue(
            ZonedDateTime.now().plusHours(hoursUntilInitialTermination).minusMinutes(1).isBefore(initialTerminationTime),
            "Initial terminationtime has not been calculated correctly"
        );
        assertTrue(
            ZonedDateTime.now().plusHours(hoursUntilInitialTermination).plusMinutes(1).isAfter(initialTerminationTime),
            "Initial terminationtime has not been calculated correctly"
        );

    }

    @Test
    public void testCreateVMServiceRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SiriDataType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri vmRequest = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        assertNull(vmRequest.getSubscriptionRequest());

        assertNotNull(vmRequest.getServiceRequest());

        List<VehicleMonitoringRequestStructure> vmRequests = vmRequest.getServiceRequest().getVehicleMonitoringRequests();
        assertNotNull(vmRequests);

        assertTrue(vmRequests.size() == 1);

        VehicleMonitoringRequestStructure request = vmRequests.get(0);
        assertNotNull(request);
    }

    @Test
    public void testCreateSXSubscription(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        final String operatorName = "AN_OPERATOR";
        subscriptionSetup.setFilterMap(
                Map.of(OperatorRefStructure.class, Set.of(operatorName))
        );

        Siri sxSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
        assertNotNull(sxSubscriptionRequest.getSubscriptionRequest());

        List<SituationExchangeSubscriptionStructure> subscriptionRequests = sxSubscriptionRequest.getSubscriptionRequest().getSituationExchangeSubscriptionRequests();
        assertNotNull(subscriptionRequests);

        assertTrue(subscriptionRequests.size() == 1);

        SituationExchangeSubscriptionStructure subscription = subscriptionRequests.get(0);
        assertNotNull(subscription.getSubscriptionIdentifier());
        assertNotNull(subscription.getSubscriptionIdentifier().getValue());
        assertEquals(subscriptionSetup.getSubscriptionId(), subscription.getSubscriptionIdentifier().getValue());

        ZonedDateTime initialTerminationTime = subscription.getInitialTerminationTime();

        assertTrue(
            ZonedDateTime.now().plusHours(hoursUntilInitialTermination).minusMinutes(1).isBefore(initialTerminationTime),
            "Initial terminationtime has not been calculated correctly"
        );
        assertTrue(
            ZonedDateTime.now().plusHours(hoursUntilInitialTermination).plusMinutes(1).isAfter(initialTerminationTime),
            "Initial terminationtime has not been calculated correctly"
        );

        var ref = subscription.getSituationExchangeRequest().getOperatorRef().getValue();
        assertEquals(ref, operatorName);
    }

    @Test
    public void testCreateSubscriptionCustomAddressfield(){

        SubscriptionSetup sxSubscriptionSetup = createSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                UUID.randomUUID().toString());

        SubscriptionSetup etSubscriptionSetup = createSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                UUID.randomUUID().toString());

        SubscriptionSetup vmSubscriptionSetup = createSubscriptionSetup(SiriDataType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                UUID.randomUUID().toString());

        Siri sxSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(sxSubscriptionSetup);
        Siri etSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(etSubscriptionSetup);
        Siri vmSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(vmSubscriptionSetup);

        assertNotNull(sxSubscriptionRequest.getSubscriptionRequest().getConsumerAddress());
        assertNotNull(etSubscriptionRequest.getSubscriptionRequest().getConsumerAddress());
        assertNotNull(vmSubscriptionRequest.getSubscriptionRequest().getConsumerAddress());

        assertNull(sxSubscriptionRequest.getSubscriptionRequest().getAddress());
        assertNull(etSubscriptionRequest.getSubscriptionRequest().getAddress());
        assertNull(vmSubscriptionRequest.getSubscriptionRequest().getAddress());

        vmSubscriptionSetup.setAddressFieldName("Address");
        etSubscriptionSetup.setAddressFieldName("Address");
        sxSubscriptionSetup.setAddressFieldName("Address");

        sxSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(sxSubscriptionSetup);
        etSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(etSubscriptionSetup);
        vmSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(vmSubscriptionSetup);

        assertNotNull(sxSubscriptionRequest.getSubscriptionRequest().getAddress());
        assertNotNull(etSubscriptionRequest.getSubscriptionRequest().getAddress());
        assertNotNull(vmSubscriptionRequest.getSubscriptionRequest().getAddress());

        assertNull(sxSubscriptionRequest.getSubscriptionRequest().getConsumerAddress());
        assertNull(etSubscriptionRequest.getSubscriptionRequest().getConsumerAddress());
        assertNull(vmSubscriptionRequest.getSubscriptionRequest().getConsumerAddress());
    }

    @Test
    public void testCreateSXServiceRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri sxRequest = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        assertNull(sxRequest.getSubscriptionRequest());

        assertNotNull(sxRequest.getServiceRequest());

        List<SituationExchangeRequestStructure> sxRequests = sxRequest.getServiceRequest().getSituationExchangeRequests();
        assertNotNull(sxRequests);

        assertTrue(sxRequests.size() == 1);

        SituationExchangeRequestStructure request = sxRequests.get(0);
        assertNotNull(request);
    }

    @Test
    public void testCreateETSubscription(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri vmSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
        assertNotNull(vmSubscriptionRequest.getSubscriptionRequest());

        List<EstimatedTimetableSubscriptionStructure> subscriptionRequests = vmSubscriptionRequest.getSubscriptionRequest().getEstimatedTimetableSubscriptionRequests();
        assertNotNull(subscriptionRequests);

        assertTrue(subscriptionRequests.size() == 1);

        EstimatedTimetableSubscriptionStructure subscription = subscriptionRequests.get(0);
        assertNotNull(subscription.getSubscriptionIdentifier());
        assertNotNull(subscription.getSubscriptionIdentifier().getValue());
        assertEquals(subscriptionSetup.getSubscriptionId(), subscription.getSubscriptionIdentifier().getValue());

        ZonedDateTime initialTerminationTime = subscription.getInitialTerminationTime();

        assertTrue(
            ZonedDateTime.now().plusHours(hoursUntilInitialTermination).minusMinutes(1).isBefore(initialTerminationTime),
            "Initial terminationtime has not been calculated correctly"
        );
        assertTrue(
            ZonedDateTime.now().plusHours(hoursUntilInitialTermination).plusMinutes(1).isAfter(initialTerminationTime),
            "Initial terminationtime has not been calculated correctly"
        );


    }

    @Test
    public void testCreateETServiceRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri etRequest = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        assertNull(etRequest.getSubscriptionRequest());

        assertNotNull(etRequest.getServiceRequest());

        List<EstimatedTimetableRequestStructure> etRequests = etRequest.getServiceRequest().getEstimatedTimetableRequests();
        assertNotNull(etRequests);

        assertTrue(etRequests.size() == 1);

        EstimatedTimetableRequestStructure request = etRequests.get(0);
        assertNotNull(request);
    }

    @Test
    public void testCreateTerminateSubscriptionRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SiriDataType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri request = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);
        assertNotNull(request);
    }

    @Test
    public void testCreateNullTerminateSubscriptionRequest(){

        Siri request = SiriObjectFactory.createTerminateSubscriptionRequest(null);
        assertNull(request);

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SiriDataType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        request = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);
        assertNotNull(request);

    }

    private SubscriptionSetup createSubscriptionSetup(SiriDataType type, SubscriptionSetup.SubscriptionMode mode, String subscriptionId) {
        return createSubscriptionSetup(type, mode, subscriptionId, "RutebankenDev");
    }

    private SubscriptionSetup createSubscriptionSetup(SiriDataType type, SubscriptionSetup.SubscriptionMode mode, String subscriptionId, String requestorRef) {
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionType(type);
        subscriptionSetup.setSubscriptionMode(mode);
        subscriptionSetup.setAddress("http://localhost");
        subscriptionSetup.setHeartbeatIntervalSeconds(30);
        subscriptionSetup.setOperatorNamespace("http://www.kolumbus.no/siri");
        subscriptionSetup.setUrlMap(new HashMap<>());
        subscriptionSetup.setVersion("1.4");
        subscriptionSetup.setVendor("dumvm");
        subscriptionSetup.setDatasetId("dum");
        subscriptionSetup.setServiceType(SubscriptionSetup.ServiceType.SOAP);
        subscriptionSetup.setSubscriptionId(subscriptionId);
        subscriptionSetup.setRequestorRef(requestorRef);
        subscriptionSetup.setDurationOfSubscriptionHours(hoursUntilInitialTermination);
        subscriptionSetup.setActive(true);
        return subscriptionSetup;
    }
}
