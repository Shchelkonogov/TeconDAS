package ru.tecon.queryBasedDAS.counter.assd;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Maksim Shchelkonogov
 * 20.12.2024
 */
@WebServlet("/console/assd")
public class ASSDConsoleServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/view/counter/assd.xhtml").forward(req, resp);
    }
}
