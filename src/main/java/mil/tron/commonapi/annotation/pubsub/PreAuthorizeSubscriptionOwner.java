package mil.tron.commonapi.annotation.pubsub;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Authorizes subscription retrieval/removal/etc if the requester is from a:
 *  - DASHBOARD_ADMIN, or
 *  - Requesting entity is a registered APP_CLIENT itself associated with given 'id' from the controller, or
 *  - Requesting entity is an APP_CLIENT_DEVELOPER of a registered app client for whom the subscription of 'id' is for
 */

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize(
        // first see if the subscription even exists
        "(@subscriberServiceImpl.subscriptionExists(#id) ? " +

                // if it does then authorize if req is from app client dev associated with subscription or
                //   req is from the app client itself (namespace in subscribers URI equals user (app) in request principal)
                "@appClientUserServiceImpl.userIsAppClientDeveloperForAppSubscription(#id, authentication.getName()) : " +

                // otherwise, if subscription does not exist, then deny
                "false )" +

                // if we get here, then DASHBOARD_ADMIN always trumps all
                "|| hasAuthority('DASHBOARD_ADMIN')")

public @interface PreAuthorizeSubscriptionOwner {
}
