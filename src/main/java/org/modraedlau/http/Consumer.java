package org.modraedlau.http;

import org.modraedlau.http.message.Decoded;
import org.modraedlau.http.message.HttpRequest;
import org.modraedlau.http.message.HttpResponse;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

/**
 * Consumer, decode and generate the response
 *
 * @author modraedlau
 */
public class Consumer implements Runnable {
    private final SimpleHttpClient client;

    Consumer(SimpleHttpClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        ByteBuffer readBuffer = client.getReadBuffer();
        Queue<HttpRequest> requestQueue = client.getRequestQueue();
        try {
            // ensures that the same client reads are executed in order
            client.lock();
            readBuffer.flip();
            // decode
            List<HttpResponse> responses = client.getDecoder().decode(readBuffer);
            readBuffer.clear();
            Iterator<HttpResponse> it = responses.iterator();
            while (it.hasNext()) {
                HttpResponse response = it.next();
                if (response.getDecoded() == Decoded.END) {
                    // remove from cached responses
                    it.remove();
                    // remove the first request from the request queue
                    HttpRequest request = requestQueue.poll();
                    if (request != null) {
                        request.getPromise().complete(response);
                    }
                }
            }
        } finally {
            client.unlock();
        }
    }
}
