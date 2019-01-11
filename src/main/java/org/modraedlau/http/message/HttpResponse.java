package org.modraedlau.http.message;

/**
 * Http response
 *
 * @author modraedlau
 */
public class HttpResponse extends HttpMessage {
    private volatile Decoded decoded;
    private int status;
    private String body = "";
    private int contentLength;
    private int length;

    public HttpResponse(int status) {
        this.status = status;
        this.decoded = Decoded.STATUS;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public void appendLine(Line line) {
        length += line.getLength();
        body += line.all();
    }

    public int getContentLength() {
        return contentLength;
    }

    public void setContentLength(int contentLength) {
        this.contentLength = contentLength;
    }

    public int getLength() {
        return length;
    }

    public Decoded getDecoded() {
        return decoded;
    }

    public void setDecoded(Decoded decoded) {
        this.decoded = decoded;
    }
}
