package mil.tron.commonapi.annotation.pubsub;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Authorizes new subscription if the request is from a:
 *  - DASHBOARD_ADMIN, or
 *  - Requesting entity is a registered APP_CLIENT itself, or
 *  - Requesting entity is an APP_CLIENT_DEVELOPER of a registered app client
 *
 *  Authorizes update of an EXISTING subscription if the request is from a:
 *  - DASHBOARD_ADMIN, or
 *  - Requesting entity is a registered APP_CLIENT_DEVELOPER of the registered app client for whom the subscription exists, or
 *  -
 */

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize(
        // first see if the subscription even exists
        "(@subscriberServiceImpl.subscriptionExists(#subscriber.getId()) ? " +

        // if it does then authorize if req is from app client dev associated with subscription or
        //   req is from the app client itself (namespace in subscribers URI equals user (app) in request principal)
        "@appClientUserServiceImpl.userIsAppClientDeveloperForAppSubscription(#subscriber.getId(), authentication.getName()) : " +

        // otherwise, if subscription is new, then any APP_CLIENT or APP_CLIENT_DEVELOPER is permitted
        "(hasAuthority('APP_CLIENT') || hasAuthority('APP_CLIENT_DEVELOPER'))) " +

        // if we get here, then DASHBOARD_ADMIN always trumps all
        "|| hasAuthority('DASHBOARD_ADMIN')")

public @interface PreAuthorizeSubscriptionCreation {

}
