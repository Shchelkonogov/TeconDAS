package ru.tecon.queryBasedDAS.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Maksim Shchelkonogov
 * 09.04.2024
 */
@WebServlet(urlPatterns = {"/console/mct/teros", "/console/mct/slave", "/console/mct/plain",
                            "/console/mct/vist", "/console/mct/sa"})
public class InWorkServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/view/inWork.xhtml").forward(req, resp);
    }
}
