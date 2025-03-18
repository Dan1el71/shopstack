package com.shopstack.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Resolves the authenticated user once, for services that need the caller's identity.
 *
 * <p>Spring Security represents an unauthenticated request with the {@code "anonymousUser"}
 * string as the principal, so a plain cast to {@link UserDetails} throws a
 * {@code ClassCastException} and the request ends as a 500. This helper turns that case into a
 * 401 with a stable error code instead.</p>
 */
public final class CurrentUser {

    /**
     * Error message returned when the request has no authenticated user.
     */
    public static final String AUTHENTICATION_REQUIRED = "authentication_required";

    private CurrentUser() {
    }

    /**
     * Returns the authenticated principal.
     *
     * @return the signed-in user.
     * @throws AuthenticationCredentialsNotFoundException when the request is anonymous or the
     *                                                   principal is not a {@link UserDetails}.
     */
    public static UserDetails require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new AuthenticationCredentialsNotFoundException(AUTHENTICATION_REQUIRED);
        }

        return userDetails;
    }
}
