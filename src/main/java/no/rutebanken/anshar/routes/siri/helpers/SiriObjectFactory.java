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

package no.rutebanken.anshar.routes.siri.helpers;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.xerces.dom.ElementNSImpl;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.CheckStatusRequestStructure;
import uk.org.siri.siri21.CheckStatusResponseStructure;
import uk.org.siri.siri21.DataReadyRequestStructure;
import uk.org.siri.siri21.DataSupplyRequestStructure;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedTimetableRequestStructure;
import uk.org.siri.siri21.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.HeartbeatNotificationStructure;
import uk.org.siri.siri21.LineDirectionStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.MessageQualifierStructure;
import uk.org.siri.siri21.MessageRefStructure;
import uk.org.siri.siri21.OperatorRefStructure;
import uk.org.siri.siri21.OtherErrorStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.ResponseStatus;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.ServiceDeliveryErrorConditionElement;
import uk.org.siri.siri21.ServiceRequest;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.SituationExchangeRequestStructure;
import uk.org.siri.siri21.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri21.SubscriptionContextStructure;
import uk.org.siri.siri21.SubscriptionQualifierStructure;
import uk.org.siri.siri21.SubscriptionRefStructure;
import uk.org.siri.siri21.SubscriptionRequest;
import uk.org.siri.siri21.SubscriptionResponseStructure;
import uk.org.siri.siri21.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri21.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri21.TerminationResponseStatusStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri21.VehicleMonitoringRefStructure;
import uk.org.siri.siri21.VehicleMonitoringRequestStructure;
import uk.org.siri.siri21.VehicleMonitoringSubscriptionStructure;
import uk.org.siri.siri21.VehicleRef;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SiriObjectFactory {

    private static final Logger logger = LoggerFactory.getLogger(SiriObjectFactory.class);

    private static final KryoPool kryoPool;

    static {
    	KryoFactory factory = () -> {
                      Kryo kryo = new Kryo();
                      kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
                      kryo.register(ElementNSImpl.class, new Serializer() {


                @Override
                public void write(Kryo kryo, Output output, Object object) {
                    throw new NotImplementedException("write-method not implemented");
                }

                @Override
                public Object read(Kryo kryo, Input input, Class type) {
                    throw new NotImplementedException("read-method not implemented");
                }

                @Override
                public Object copy(Kryo kryo, Object original) {

                    return ((ElementNSImpl) original).cloneNode(true);
                }
            });

          // configure kryo instance, customize settings
          return kryo;
        };

    	kryoPool = new KryoPool.Builder(factory).softReferences().build();

    }

    @Autowired
    private AnsharConfiguration configuration;

    public final Instant serverStartTime;

    public SiriObjectFactory(@Autowired Instant serverStartTime) {
        this.serverStartTime = serverStartTime;
    }
    
    public static Siri createSubscriptionRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject(subscriptionSetup.getVersion());

        SubscriptionRequest request = null;

        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)) {
            request = createSituationExchangeSubscriptionRequest(subscriptionSetup.getRequestorRef(),subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap(),
                    subscriptionSetup.getAddressFieldName(),
                    subscriptionSetup.getIncrementalUpdates(),
                    subscriptionSetup.getPreviewInterval(),
                    subscriptionSetup.getVersion()
                    );
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
            request = createVehicleMonitoringSubscriptionRequest(subscriptionSetup.getRequestorRef(),
                    subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap(),
                    subscriptionSetup.getUpdateInterval(),
                    subscriptionSetup.getChangeBeforeUpdates(),
                    subscriptionSetup.getAddressFieldName(),
                    subscriptionSetup.getIncrementalUpdates(),
                    subscriptionSetup.getVehicleMonitoringRefValue(),
                    subscriptionSetup.getVersion()
                    );
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
            request = createEstimatedTimetableSubscriptionRequest(subscriptionSetup.getRequestorRef(),subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap(),
                    subscriptionSetup.getAddressFieldName(),
                    subscriptionSetup.getIncrementalUpdates(),
                    subscriptionSetup.getPreviewInterval(),
                    subscriptionSetup.getChangeBeforeUpdates(),
                    subscriptionSetup.getVersion()
            );
        }
        siri.setSubscriptionRequest(request);

        return siri;
    }


    public static Siri createServiceRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject(subscriptionSetup.getVersion());

        ServiceRequest request = new ServiceRequest();
        request.setRequestTimestamp(ZonedDateTime.now());
        request.setRequestorRef(createRequestorRef(subscriptionSetup.getRequestorRef()));

        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)) {
            request.getSituationExchangeRequests().add(createSituationExchangeRequestStructure(
                    subscriptionSetup.getPreviewInterval(),
                    subscriptionSetup.getVersion())
            );

        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
            request.getVehicleMonitoringRequests().add(createVehicleMonitoringRequestStructure(subscriptionSetup.getVersion()));
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
            request.getEstimatedTimetableRequests().add(createEstimatedTimetableRequestStructure(
                    subscriptionSetup.getPreviewInterval(),
                    subscriptionSetup.getVersion())
            );
        }

        siri.setServiceRequest(request);

        return siri;
    }


    public static Siri createDataSupplyRequest(SubscriptionSetup subscriptionSetup, Boolean allData) {
        Siri siri = createSiriObject(subscriptionSetup.getVersion());

        DataSupplyRequestStructure request = new DataSupplyRequestStructure();
        request.setRequestTimestamp(ZonedDateTime.now());
        request.setConsumerRef(createRequestorRef(subscriptionSetup.getRequestorRef()));
        request.setAllData(allData);

        siri.setDataSupplyRequest(request);

        return siri;
    }

    public static Siri createCheckStatusRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject(subscriptionSetup.getVersion());

        CheckStatusRequestStructure statusRequest = new CheckStatusRequestStructure();
        statusRequest.setRequestTimestamp(ZonedDateTime.now());
        statusRequest.setMessageIdentifier(createMessageIdentifier());
        statusRequest.setRequestorRef(createRequestorRef(subscriptionSetup.getRequestorRef()));
        siri.setCheckStatusRequest(statusRequest);

        return siri;
    }

    private static SituationExchangeRequestStructure createSituationExchangeRequestStructure(Duration previewInterval, @Nonnull String version) {
        SituationExchangeRequestStructure sxRequest = new SituationExchangeRequestStructure();
        sxRequest.setRequestTimestamp(ZonedDateTime.now());
        sxRequest.setVersion(version);
        sxRequest.setMessageIdentifier(createMessageIdentifier());
        if (previewInterval != null) {
            sxRequest.setPreviewInterval(previewInterval);
        }
        return sxRequest;
    }



    private static VehicleMonitoringRequestStructure createVehicleMonitoringRequestStructure(@Nonnull String version) {
        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion(version);
        vmRequest.setMessageIdentifier(createMessageIdentifier());
        return vmRequest;
    }

    private static EstimatedTimetableRequestStructure createEstimatedTimetableRequestStructure(Duration previewInterval, @Nonnull String version) {
        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion(version);
        etRequest.setMessageIdentifier(createMessageIdentifier());
        if (previewInterval != null) {
            etRequest.setPreviewInterval(previewInterval);
        }
        return etRequest;
    }

    private static SubscriptionRequest createSituationExchangeSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap, String addressFieldName, Boolean incrementalUpdates, Duration previewInterval, @Nonnull String version) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address, addressFieldName);

        SituationExchangeRequestStructure sxRequest = createSituationExchangeRequestStructure(null, version);

        if (previewInterval != null) {
            sxRequest.setPreviewInterval(previewInterval);
        }

        if (filterMap != null) {
            Set<Object> vehicleRefs = filterMap.get(VehicleRef.class);
            if (vehicleRefs != null && !vehicleRefs.isEmpty()) {
                Object next = vehicleRefs.iterator().next();
                if (next instanceof VehicleRef)  {
                    sxRequest.setVehicleRef((VehicleRef) next);
                }
            }
            if (filterMap.containsKey(OperatorRefStructure.class)) {
                Set<Object> operatorRefs = filterMap.get(OperatorRefStructure.class);
                for (Object operatorRef : operatorRefs) {
                    if (operatorRef instanceof String) {
                        var refStructure = new OperatorRefStructure();
                        refStructure.setValue((String) operatorRef);
                        sxRequest.setOperatorRef(refStructure);
                    }
                }
            }
        }

        SituationExchangeSubscriptionStructure sxSubscriptionReq = new SituationExchangeSubscriptionStructure();
        sxSubscriptionReq.setSituationExchangeRequest(sxRequest);
        sxSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        sxSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        sxSubscriptionReq.setSubscriberRef(request.getRequestorRef());

        sxSubscriptionReq.setIncrementalUpdates(incrementalUpdates);

        request.getSituationExchangeSubscriptionRequests().add(sxSubscriptionReq);

        return request;
    }

    private static SubscriptionRequest createVehicleMonitoringSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap, Duration updateInterval, Duration changeBeforeUpdates, String addressFieldName, Boolean incrementalUpdates, String vehicleMonitoringRefValue, @Nonnull String version) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef,heartbeatInterval, address, addressFieldName);

        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion(version);

        if (vehicleMonitoringRefValue != null) {
            VehicleMonitoringRefStructure vehicleMonitoringRef = new VehicleMonitoringRefStructure();
            vehicleMonitoringRef.setValue(vehicleMonitoringRefValue);
            vmRequest.setVehicleMonitoringRef(vehicleMonitoringRef);
        }

        if (filterMap != null) {
            Set lineRefs = filterMap.get(LineRef.class);
            if (lineRefs != null && !lineRefs.isEmpty()) {
                Object next = lineRefs.iterator().next();
                if (next instanceof LineRef) {
                    vmRequest.setLineRef((LineRef) next);
                }
            }
            Set<Object> vehicleRefs = filterMap.get(VehicleRef.class);
            if (vehicleRefs != null && !vehicleRefs.isEmpty()) {
                Object next = vehicleRefs.iterator().next();
                if (next instanceof VehicleRef)  {
                    vmRequest.setVehicleRef((VehicleRef) next);
                }
            }
        }

        VehicleMonitoringSubscriptionStructure vmSubscriptionReq = new VehicleMonitoringSubscriptionStructure();
        vmSubscriptionReq.setVehicleMonitoringRequest(vmRequest);
        vmSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        vmSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        vmSubscriptionReq.setSubscriberRef(request.getRequestorRef());

        if (updateInterval != null) {
            //Requesting updates every second
            vmSubscriptionReq.setUpdateInterval(updateInterval);
        }
        vmSubscriptionReq.setIncrementalUpdates(incrementalUpdates);
        if (changeBeforeUpdates != null) {
            vmSubscriptionReq.setChangeBeforeUpdates(changeBeforeUpdates);
        }

        request.getVehicleMonitoringSubscriptionRequests().add(vmSubscriptionReq);

        return request;
    }


    private static SubscriptionRequest createEstimatedTimetableSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap, String addressFieldName, Boolean incrementalUpdates, Duration previewInterval, Duration changeBeforeUpdates,
            @Nonnull String version
    ) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address, addressFieldName);

        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion(version);

        if (previewInterval != null) {
            etRequest.setPreviewInterval(previewInterval);
        }

        if (filterMap != null) {
            if (filterMap.size() > 0) {

                if (filterMap.containsKey(LineDirectionStructure.class)) {
                    EstimatedTimetableRequestStructure.Lines lines = new EstimatedTimetableRequestStructure.Lines();
                    Set lineRefs = filterMap.get(LineDirectionStructure.class);
                    for (Object lineref : lineRefs) {
                        if (lineref instanceof LineDirectionStructure) {
                            lines.getLineDirections().add((LineDirectionStructure) lineref);
                        }
                    }
                    if (!lines.getLineDirections().isEmpty()) {
                        etRequest.setLines(lines);
                    }
                }

                if (filterMap.containsKey(OperatorRefStructure.class)) {
                    Set<Object> operatorRefs = filterMap.get(OperatorRefStructure.class);
                    for (Object operatorRef : operatorRefs) {
                        if (operatorRef instanceof OperatorRefStructure) {
                            etRequest.getOperatorReves().add((OperatorRefStructure) operatorRef);
                        }
                    }
                }
            }
        }

        EstimatedTimetableSubscriptionStructure etSubscriptionReq = new EstimatedTimetableSubscriptionStructure();
        etSubscriptionReq.setEstimatedTimetableRequest(etRequest);
        etSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        etSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        etSubscriptionReq.setSubscriberRef(request.getRequestorRef());
        if (changeBeforeUpdates != null) {
            etSubscriptionReq.setChangeBeforeUpdates(changeBeforeUpdates);
        }

        etSubscriptionReq.setIncrementalUpdates(incrementalUpdates);

        request.getEstimatedTimetableSubscriptionRequests().add(etSubscriptionReq);

        return request;
    }

    private static SubscriptionRequest createSubscriptionRequest(String requestorRef, Duration heartbeatInterval, String address, String addressFieldName) {
        SubscriptionRequest request = new SubscriptionRequest();
        request.setRequestorRef(createRequestorRef(requestorRef));
        request.setMessageIdentifier(createMessageIdentifier(UUID.randomUUID().toString()));

        if (addressFieldName != null && addressFieldName.equalsIgnoreCase("Address")) {
            request.setAddress(address);
        } else {
            request.setConsumerAddress(address);
        }

        request.setRequestTimestamp(ZonedDateTime.now());

        if (heartbeatInterval != null) {
            SubscriptionContextStructure ctx = new SubscriptionContextStructure();
            ctx.setHeartbeatInterval(heartbeatInterval);

            request.setSubscriptionContext(ctx);
        }
        return request;
    }

    public static Siri createTerminateSubscriptionRequest(SubscriptionSetup subscriptionSetup) {
        if (subscriptionSetup == null) {
            return null;
        }
        return createTerminateSubscriptionRequest(subscriptionSetup.getSubscriptionId(), createRequestorRef(subscriptionSetup.getRequestorRef()), subscriptionSetup.getVersion());
    }

    private static Siri createTerminateSubscriptionRequest(String subscriptionId, RequestorRef requestorRef, String version) {
        if (requestorRef == null || requestorRef.getValue() == null) {
            logger.warn("RequestorRef cannot be null");
            return null;
        }
        TerminateSubscriptionRequestStructure terminationReq = new TerminateSubscriptionRequestStructure();

        terminationReq.setRequestTimestamp(ZonedDateTime.now());
        terminationReq.getSubscriptionReves().add(createSubscriptionIdentifier(subscriptionId));
        terminationReq.setRequestorRef(requestorRef);
        terminationReq.setMessageIdentifier(createMessageIdentifier(UUID.randomUUID().toString()));

        Siri siri = createSiriObject(version);
        siri.setTerminateSubscriptionRequest(terminationReq);
        return siri;
    }

    public static RequestorRef createRequestorRef(String value) {
        if(value == null) {
            value = UUID.randomUUID().toString();
        }
        RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue(value);
        return requestorRef;
    }

    private static SubscriptionQualifierStructure createSubscriptionIdentifier(String subscriptionId) {
        SubscriptionQualifierStructure subscriptionRef = new SubscriptionQualifierStructure();
        subscriptionRef.setValue(subscriptionId);
        return subscriptionRef;
    }


    private static SubscriptionRefStructure createSubscriptionRef(String subscriptionId) {
        SubscriptionRefStructure subscriptionRef = new SubscriptionRefStructure();
        subscriptionRef.setValue(subscriptionId);
        return subscriptionRef;
    }

    private static MessageQualifierStructure createMessageIdentifier(String value) {
        MessageQualifierStructure msgId = new MessageQualifierStructure();
        msgId.setValue(value);
        return msgId;
    }

    private static MessageQualifierStructure createMessageIdentifier() {
        return createMessageIdentifier(UUID.randomUUID().toString());
    }

    private static MessageRefStructure createMessageRef(String value) {
        MessageRefStructure msgId = new MessageRefStructure();
        msgId.setValue(value);
        return msgId;
    }

    private static MessageRefStructure createMessageRef() {
        return createMessageRef(UUID.randomUUID().toString());
    }

    public Siri createSXServiceDelivery(Collection<PtSituationElement> elements) {
        Siri siri = createSiriObject(SiriHelper.FALLBACK_SIRI_VERSION);
        ServiceDelivery delivery = createServiceDelivery();
        SituationExchangeDeliveryStructure deliveryStructure = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().addAll(elements);
        deliveryStructure.setSituations(situations);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getSituationExchangeDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createVMServiceDelivery(Collection<VehicleActivityStructure> elements) {
        Siri siri = createSiriObject(SiriHelper.FALLBACK_SIRI_VERSION);
        ServiceDelivery delivery = createServiceDelivery();
        VehicleMonitoringDeliveryStructure deliveryStructure = new VehicleMonitoringDeliveryStructure();
        deliveryStructure.setVersion(SiriHelper.FALLBACK_SIRI_VERSION);
        deliveryStructure.getVehicleActivities().addAll(elements);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getVehicleMonitoringDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createETServiceDelivery(Collection<EstimatedVehicleJourney> elements) {
        Siri siri = createSiriObject(SiriHelper.FALLBACK_SIRI_VERSION);
        ServiceDelivery delivery = createServiceDelivery();
        EstimatedTimetableDeliveryStructure deliveryStructure = new EstimatedTimetableDeliveryStructure();
        deliveryStructure.setVersion(SiriHelper.FALLBACK_SIRI_VERSION);
        EstimatedVersionFrameStructure estimatedVersionFrameStructure = new EstimatedVersionFrameStructure();
        estimatedVersionFrameStructure.setRecordedAtTime(ZonedDateTime.now());
        estimatedVersionFrameStructure.getEstimatedVehicleJourneies().addAll(elements);
        deliveryStructure.getEstimatedJourneyVersionFrames().add(estimatedVersionFrameStructure);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());

        delivery.getEstimatedTimetableDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    private ServiceDelivery createServiceDelivery() {
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.setResponseTimestamp(ZonedDateTime.now());
        if (configuration != null && configuration.getProducerRef() != null) {
            delivery.setProducerRef(createRequestorRef(configuration.getProducerRef()));
        }
        return delivery;
    }

    public Siri createHeartbeatNotification(String requestorRef) {
        return createHeartbeatNotification(requestorRef, SiriHelper.FALLBACK_SIRI_VERSION);
    }

    public Siri createHeartbeatNotification(String requestorRef, String version) {
        Siri siri = createSiriObject(version);
        HeartbeatNotificationStructure heartbeat = new HeartbeatNotificationStructure();
        heartbeat.setStatus(true);
        heartbeat.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        heartbeat.setRequestTimestamp(ZonedDateTime.now());
        heartbeat.setProducerRef(createRequestorRef(requestorRef));
        siri.setHeartbeatNotification(heartbeat);
        return siri;
    }

    public Siri createCheckStatusResponse() {
        Siri siri = createSiriObject(SiriHelper.FALLBACK_SIRI_VERSION);
        CheckStatusResponseStructure response = new CheckStatusResponseStructure();
        response.setStatus(true);
        response.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        response.setShortestPossibleCycle(Duration.ofSeconds(60));
        siri.setCheckStatusResponse(response);
        return siri;
    }

    private static Siri createSiriObject(@Nonnull String version) {
        Siri siri = new Siri();
        siri.setVersion(version);
        return siri;
    }

    public Siri createSubscriptionResponse(String subscriptionRef, boolean status, String errorText, String version) {
        Siri siri = createSiriObject(version);
        SubscriptionResponseStructure response = new SubscriptionResponseStructure();
        response.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        response.setRequestMessageRef(createMessageRef());
        response.setResponderRef(createRequestorRef(subscriptionRef));
        response.setResponseTimestamp(ZonedDateTime.now());


        ResponseStatus responseStatus = new ResponseStatus();
        responseStatus.setResponseTimestamp(ZonedDateTime.now());
        responseStatus.setRequestMessageRef(createMessageRef());
        responseStatus.setSubscriptionRef(createSubscriptionRef(subscriptionRef));
        responseStatus.setStatus(status);

        if (errorText != null) {
            ServiceDeliveryErrorConditionElement error = new ServiceDeliveryErrorConditionElement();
            OtherErrorStructure otherError = new OtherErrorStructure();
            otherError.setErrorText(errorText);
            error.setOtherError(otherError);
            responseStatus.setErrorCondition(error);
        }

        response.getResponseStatuses().add(responseStatus);

        siri.setSubscriptionResponse(response);
        return siri;
    }

    public Siri createTerminateSubscriptionResponse(String subscriptionRef) {
        Siri siri = createSiriObject(SiriHelper.FALLBACK_SIRI_VERSION);
        TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();
        TerminationResponseStatusStructure status = new TerminationResponseStatusStructure();
        status.setSubscriptionRef(createSubscriptionRef(subscriptionRef));
        status.setResponseTimestamp(ZonedDateTime.now());
        status.setStatus(true);

        response.getTerminationResponseStatuses().add(status);
        siri.setTerminateSubscriptionResponse(response);
        return siri;
    }

    public Siri createDataReadyNotification() {
        Siri siri = createSiriObject(SiriHelper.FALLBACK_SIRI_VERSION);
        DataReadyRequestStructure dataReadyNotification = new DataReadyRequestStructure();
        dataReadyNotification.setRequestTimestamp(ZonedDateTime.now());
        siri.setDataReadyNotification(dataReadyNotification);
        return siri;
    }

    /**
     * Creates a deep copy of provided object
     * @param siri
     * @return
     * @throws JAXBException
     */
    public static Siri deepCopy(Siri siri) {
    	Kryo kryo = kryoPool.borrow();
        try {
        	return kryo.copy(siri);
        } finally {
        	kryoPool.release(kryo);
        }
    }

    /**
     * Creates a deep copy of provided object
     * @param estimatedVehicleJourney
     * @return
     * @throws JAXBException
     */
    public static EstimatedVehicleJourney deepCopy(EstimatedVehicleJourney estimatedVehicleJourney) {
    	Kryo kryo = kryoPool.borrow();
        try {
        	return kryo.copy(estimatedVehicleJourney);
        } finally {
        	kryoPool.release(kryo);
        }
    }
}
