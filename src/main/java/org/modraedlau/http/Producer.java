package org.modraedlau.http;

import org.modraedlau.http.message.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Queue;

/**
 * Producer, writes the request in the queue to the channel
 *
 * @author modraedlau
 */
public class Producer implements Runnable {
    private static Logger logger = LoggerFactory.getLogger(Producer.class);

    private final SimpleHttpClient client;

    Producer(SimpleHttpClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        SocketChannel channel = client.getSocketChannel();
        ByteBuffer writeBuffer = client.getWriteBuffer();
        Queue<HttpRequest> requestQueue = client.getRequestQueue();

        // Waiting for a connected
        if (!client.isConnected()) {
            try {
                client.waitConnected();
            } catch (InterruptedException e) {
                logger.error("Waiting for connection to be interrupted!", e);
            }
        }

        // Take the request from the queue and write to the channel
        try {
            // In order to reuse the writeBuffer,
            // here the lock prevents the state of the writeBuffer from being incorrectly modified.
            client.lock();
            for (HttpRequest request : requestQueue) {
                // Find a request that has not been written
                if (request != null && !request.isWritten()) {
                    writeBuffer.put(request.toString().getBytes(StandardCharsets.UTF_8));
                    writeBuffer.flip();
                    try {
                        channel.write(writeBuffer);
                        request.setWritten(true);
                    } catch (IOException e) {
                        logger.error("Error writing", e);

                        // Close channel
                        client.closeByException(e);
                    }
                    writeBuffer.clear();
                }
            }
        } finally {
            client.unlock();
        }
    }
}
