package org.modraedlau.http.message;

/**
 * Http header
 *
 * @author modraedlau
 */
public class Header {
    public static final String SERVER = "Server";
    public static final String CONTENT_LENGTH = "Content-Length";

    private String name;
    private String value;

    public Header(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return name + ": " + value;
    }
}
