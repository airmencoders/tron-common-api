package mil.tron.commonapi.pubsub.listeners;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.pubsub.EventPublisher;
import mil.tron.commonapi.security.AppClientPreAuthFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Listens for changes to {@link Organization} entities
 */
public class OrganizationEntityListener {
    private static final Log APP_LOGGER = LogFactory.getLog(CommonApiLogger.class);
    private static final String ORGANIZATION_CREATE_MSG = "New Organization Added";
    private static final String ORGANIZATION_DATA_CHANGED = "Organization Data Changed";
    private static final String ORGANIZATION_DELETE_MSG = "Organization Removed";

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private HttpServletRequest servletRequest;

    /**
     * Removes members/subordinate organizations fields from the org entity
     * @return Slimmed down Organizational entity with only Organizational ID
     */
    private Map<String, Object> stripOrganization(Organization org) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", org.getId());
        return map;
    }

    /**
     * Fires off a request to the EventPublisher to tell subscribers that something changed with Organization
     * @param organization The {@link Organization} entity that was created
     */
    @PostPersist
    public void afterAnyCreation(Organization organization) {
        APP_LOGGER.info("[PUB-SUB EVENT] create complete for Organization ID: " + organization.getId());
        publisher.publishEvent(
                EventType.ORGANIZATION_CREATE,
                ORGANIZATION_CREATE_MSG,
                organization.getClass().getName(),
                stripOrganization(organization),
                servletRequest.getHeader(AppClientPreAuthFilter.XFCC_HEADER_NAME)
        );
    }

    /**
     * Fires off a request to the EventPublisher to tell subscribers that something changed with Organization
     * @param organization The {@link Organization} entity that was changed
     */
    @PostUpdate
    public void afterAnyUpdate(Organization organization) {
        APP_LOGGER.info("[PUB-SUB EVENT] change/update complete for Organization ID: " + organization.getId());
        publisher.publishEvent(
                EventType.ORGANIZATION_CHANGE,
                ORGANIZATION_DATA_CHANGED,
                organization.getClass().getName(),
                stripOrganization(organization),
                servletRequest.getHeader(AppClientPreAuthFilter.XFCC_HEADER_NAME)
        );
    }

    /**
     * Fires off a request to the EventPublisher to tell subscribers that something changed with Organization
     * @param organization The {@link Organization} entity that was deleted
     */
    @PostRemove
    public void afterAnyRemoval(Organization organization) {
        APP_LOGGER.info("[PUB-SUB EVENT] delete complete for Organization ID: " + organization.getId());
        publisher.publishEvent(
                EventType.ORGANIZATION_DELETE,
                ORGANIZATION_DELETE_MSG,
                organization.getClass().getName(),
                stripOrganization(organization),
                servletRequest.getHeader(AppClientPreAuthFilter.XFCC_HEADER_NAME)
        );
    }
}
