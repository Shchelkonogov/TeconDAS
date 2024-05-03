package ru.tecon.queryBasedDAS.oidc;

import fish.payara.security.openid.api.OpenIdContext;

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Maksim Shchelkonogov
 * 17.04.2024
 */
@WebServlet("/console/logout")
public class LogoutServlet extends HttpServlet {

    @Inject
    private OpenIdContext openIdContext;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        openIdContext.logout(req, resp);
    }
}
