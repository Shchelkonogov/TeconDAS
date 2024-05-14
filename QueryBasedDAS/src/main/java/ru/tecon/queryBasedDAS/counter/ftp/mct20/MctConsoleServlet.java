package ru.tecon.queryBasedDAS.counter.ftp.mct20;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Maksim Shchelkonogov
 * 07.05.2024
 */
@WebServlet("/console/mct")
public class MctConsoleServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/view/counter/mct.xhtml").forward(req, resp);
    }
}
