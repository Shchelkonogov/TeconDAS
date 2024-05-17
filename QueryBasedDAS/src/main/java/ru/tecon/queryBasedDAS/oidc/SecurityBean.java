package ru.tecon.queryBasedDAS.oidc;

import fish.payara.security.annotations.ClaimsDefinition;
import fish.payara.security.annotations.LogoutDefinition;
import fish.payara.security.annotations.OpenIdAuthenticationDefinition;

/**
 * @author Maksim Shchelkonogov
 * 12.04.2024
 */
@OpenIdAuthenticationDefinition(
        providerURI = "https://disp-drv-gui.mipcnet.org/auth/realms/TeconKeycloak",
        clientId = "console",
        clientSecret = "VOaR8Uh86f6CNE1lHAj6qGEd0oqyJcB2",
        redirectURI = "${baseURL}/callback",
        scope = {"openid"},
        claimsDefinition = @ClaimsDefinition(
                callerNameClaim="preferred_username",
                callerGroupsClaim="groups"
        ),
        extraParameters = {
                "idps_to_show=all"
        },
        tokenAutoRefresh = true,
        logout = @LogoutDefinition(
                notifyProvider = true,
                // можно не писать и тогда будет страница от keycloak
                redirectURI = "${baseURL}/console"
        )
)
public class SecurityBean {
}
