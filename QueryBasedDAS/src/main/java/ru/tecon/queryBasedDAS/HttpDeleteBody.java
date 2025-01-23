package ru.tecon.queryBasedDAS;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

import java.net.URI;

/**
 * @author Maksim Shchelkonogov
 * 27.12.2024
 */
public class HttpDeleteBody extends HttpEntityEnclosingRequestBase {

    public HttpDeleteBody(URI uri) {
        super();
        this.setURI(uri);
    }

    @Override
    public String getMethod() {
        return "DELETE";
    }
}
