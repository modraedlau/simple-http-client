package org.modraedlau.http.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Http message, common parent request and response
 *
 * @author modraedlau
 */
public class HttpMessage {
    public static final String HTTP_1_0 = "HTTP/1.0";
    public static final String HTTP_1_1 = "HTTP/1.1";
    public static final String HTTP_2_0 = "HTTP/2.0";

    private List<Header> headers;

    public HttpMessage() {
        headers = new ArrayList<>();
    }

    public void setHeader(String name, String value) {
        headers.add(new Header(name, value));
    }

    public Header getHeader(String name) {
        for (Header header : headers) {
            if (header.getName().equalsIgnoreCase(name)) {
                return header;
            }
        }
        return null;
    }

    public List<Header> getHeaders() {
        return headers;
    }
}
