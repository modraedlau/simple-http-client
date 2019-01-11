package org.modraedlau.http.message;

import java.util.concurrent.CompletableFuture;

/**
 * Http request
 *
 * @author modraedlau
 */
public class HttpRequest extends HttpMessage {

    private HttpMethod method;

    private String path;

    private CompletableFuture<HttpResponse> promise;

    private volatile boolean written = false;

    public HttpRequest(HttpMethod method, String path, CompletableFuture<HttpResponse> promise) {
        this.method = method;
        this.path = path;
        this.promise = promise;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public CompletableFuture<HttpResponse> getPromise() {
        return promise;
    }

    public boolean isWritten() {
        return written;
    }

    public void setWritten(boolean written) {
        this.written = written;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(method.toString());
        builder.append(" ");
        builder.append(path);
        builder.append(" ");
        builder.append(HttpMessage.HTTP_1_1);
        builder.append("\n");
        for (Header header : getHeaders()) {
            builder.append(header.getName());
            builder.append(": ");
            builder.append(header.getValue());
        }
        builder.append("\n");
        return builder.toString();
    }
}
