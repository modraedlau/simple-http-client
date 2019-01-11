package org.modraedlau.http.message;

/**
 * Line
 *
 * @author modraedlau
 */
public class Line {
    private String line;
    private boolean includeCR;
    private int length;

    public Line(String line, boolean includeCR, int length) {
        this.line = line;
        this.includeCR = includeCR;
        this.length = length;
    }

    public String getLine() {
        return line;
    }

    public int getLength() {
        return length;
    }

    public String all() {
        return line + (includeCR ? "\r" : "") + "\n";
    }

    @Override
    public String toString() {
        return line;
    }
}
