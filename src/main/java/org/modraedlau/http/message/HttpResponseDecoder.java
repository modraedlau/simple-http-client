package org.modraedlau.http.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

/**
 * Http response decoder
 *
 * @author modraedlau
 */
public class HttpResponseDecoder {
    private static final byte CR = 13;
    private static final byte LF = 10;

    /**
     * line buffer
     */
    private byte[] lineBuffer = new byte[128 * 1024];

    /**
     * index for line buffer
     */
    private int index = 0;

    /**
     * response list
     */
    private List<HttpResponse> responses = new LinkedList<>();

    public List<HttpResponse> decode(ByteBuffer in) {
        Line line;
        while ((line = readLine(in)) != null) {
            if (line.getLine().startsWith(HttpMessage.HTTP_1_1)
                || line.getLine().startsWith(HttpMessage.HTTP_1_0)
                || line.getLine().startsWith(HttpMessage.HTTP_2_0)) {
                // status line
                String[] statusArray = line.getLine().split("\\s+");
                if (statusArray.length > 2) {
                    int status = Integer.parseInt(statusArray[1].trim());
                    responses.add(new HttpResponse(status));
                }
            } else if (line.getLine().isEmpty()) {
                // empty line
                for (HttpResponse response : responses) {
                    if (response.getDecoded() == Decoded.STATUS) {
                        response.setDecoded(Decoded.BLANK);
                        break;
                    }
                }
            } else {
                for (HttpResponse response : responses) {
                    if (response.getDecoded() == Decoded.STATUS) {
                        // headers
                        String[] headerArray = line.getLine().split(":");
                        if (headerArray.length == 2) {
                            response.setHeader(headerArray[0].trim(), headerArray[1].trim());
                            Header header = response.getHeader(Header.CONTENT_LENGTH);
                            if (header != null) {
                                int contentLength = Integer.parseInt(header.getValue());
                                response.setContentLength(contentLength);
                            }
                            break;
                        }
                    } else {
                        if (response.getDecoded() == Decoded.BLANK) {
                            // decode body

                            // by `Content-Length`
                            response.appendLine(line);
                            if (response.getLength() >= response.getContentLength()) {
                                // The length reaches the total length of the content
                                response.setDecoded(Decoded.END);
                            }

                            // TODO：by `Transfer-Encoding: chunked`

                            break;
                        }
                    }
                }
            }
        }
        return responses;
    }

    /**
     * Read a line
     *
     * @param in byte buffer
     * @return line
     */
    private Line readLine(ByteBuffer in) {
        byte read;
        boolean more = true;
        boolean includeCR = false;
        while (in.hasRemaining() && more) {
            read = in.get();
            if (read == LF) {
                // 行尾
                more = false;
            } else if (read == CR) {
                includeCR = true;
            } else {
                // 其他内容放入行缓存　
                lineBuffer[index] = read;
                index++;
            }
        }
        Line line = null;
        if (!more) {
            line = new Line(new String(lineBuffer, 0, index, StandardCharsets.UTF_8),
                includeCR, includeCR ? index + 2 : index + 1);
            reset();
        }
        return line;
    }

    private void reset() {
        this.index = 0;
    }
}
