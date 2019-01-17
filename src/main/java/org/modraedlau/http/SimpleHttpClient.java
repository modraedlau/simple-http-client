package org.modraedlau.http;

import org.modraedlau.http.message.HttpMethod;
import org.modraedlau.http.message.HttpRequest;
import org.modraedlau.http.message.HttpResponse;
import org.modraedlau.http.message.HttpResponseDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple http client
 *
 * @author modraedlau
 */
public class SimpleHttpClient {
    private static Logger logger = LoggerFactory.getLogger(Producer.class);

    private static final int DEFAULT_PORT = 80;

    private static final long DEFAULT_TIMEOUT = 10000;

    private final URI uri;

    private final SocketChannel socketChannel;

    /**
     * connection state
     */
    private volatile int connection;

    private static final int DISCONNCTED = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECTED = 2;

    private volatile WaitNode waiters;

    /**
     * read buffer
     */
    private final ByteBuffer readBuffer;

    /**
     * write buffer
     */
    private final ByteBuffer writeBuffer;

    /**
     * request queue
     */
    private final Queue<HttpRequest> requestQueue;

    private volatile ExecutorService executor;

    private volatile ScheduledExecutorService scheduled;

    private final HttpResponseDecoder decoder;

    /**
     * exclusive lock
     */
    private final Lock lock;

    public SimpleHttpClient(String url) throws IOException {
        this.uri = URI.create(url);

        readBuffer = ByteBuffer.allocateDirect(10 * 1024);
        writeBuffer = ByteBuffer.allocateDirect(1024);

        requestQueue = new ConcurrentLinkedQueue<>();

        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        decoder = new HttpResponseDecoder();

        lock = new ReentrantLock();
    }

    /**
     * Asynchronous GET
     *
     * @param path request path
     * @return result future
     */
    public CompletableFuture<HttpResponse> get(String path) {
        CompletableFuture<HttpResponse> promise = new CompletableFuture<>();
        scheduled.schedule(() -> {
            TimeoutException timeoutException = new TimeoutException("Request time out: " + DEFAULT_TIMEOUT + "ms");
            promise.completeExceptionally(timeoutException);
            closeByException(timeoutException);
        }, DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        try {
            checkConnection();
        } catch (IOException e) {
            promise.completeExceptionally(e);
            return promise;
        }
        HttpRequest request = new HttpRequest(HttpMethod.GET, path, promise);
        requestQueue.offer(request);
        executor.execute(new Producer(this));
        return promise;
    }

    /**
     * Check the connection status, connect if not connected
     *
     * @throws IOException io exception
     */
    private void checkConnection() throws IOException {
        if (connection == DISCONNCTED) {
            connectingState();
            socketChannel.connect(new InetSocketAddress(this.uri.getHost(),
                this.uri.getPort() == -1 ? DEFAULT_PORT : this.uri.getPort()));
        }
    }

    void closeByException(Exception e) {
        try {
            socketChannel.close();
        } catch (IOException e1) {
            logger.error("Error closing channel", e1);
        } finally {
            disconnectedState();
            for (HttpRequest request : requestQueue) {
                request.getPromise().completeExceptionally(e);
            }
            requestQueue.clear();
        }
    }

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    void setScheduled(ScheduledExecutorService scheduled) {
        this.scheduled = scheduled;
    }

    /**
     * Is connected?
     *
     * @return return true if connected, else false
     */
    public boolean isConnected() {
        return connection == CONNECTED;
    }

    void connectingState() {
        UNSAFE.compareAndSwapInt(this, connectionOffset, DISCONNCTED, CONNECTING);
    }

    void connectedState() {
        UNSAFE.compareAndSwapInt(this, connectionOffset, CONNECTING, CONNECTED);
    }

    void disconnectedState() {
        if (!UNSAFE.compareAndSwapInt(this, connectionOffset, CONNECTED, DISCONNCTED)) {
            UNSAFE.compareAndSwapInt(this, connectionOffset, CONNECTING, DISCONNCTED);
        }
    }

    void waitConnected() throws InterruptedException {
        awaitConnection();
    }

    void notifyConnected() {
        finishConnection();
    }

    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;

        WaitNode() {
            thread = Thread.currentThread();
        }
    }

    private int awaitConnection()
        throws InterruptedException {
        WaitNode q = null;
        boolean queued = false;
        while (true) {
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }
            int conn = connection;
            if (conn > CONNECTING) {
                if (q != null) {
                    q.thread = null;
                }
                return conn;
            } else if (conn == CONNECTING) {
                Thread.yield();
            } else if (q == null) {
                q = new WaitNode();
            } else if (!queued) {
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                    q.next = waiters, q);
            } else {
                LockSupport.park(this);
            }
        }
    }

    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            while (true) {
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null) {
                        pred = q;
                    } else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) {
                            continue retry;
                        }
                    } else if (!UNSAFE.compareAndSwapObject(this, waitersOffset, q, s)) {
                        continue retry;
                    }
                }
                break;
            }
        }
    }

    private void finishConnection() {
        for (WaitNode q; (q = waiters) != null; ) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                while (true) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null) {
                        break;
                    }
                    q.next = null;
                    q = next;
                }
                break;
            }
        }
    }

    void executeConsumer() throws IOException {
        try {
            lock.lock();
            while (!Thread.currentThread().isInterrupted() &&
                socketChannel.isOpen() && socketChannel.read(readBuffer) != -1) {
                if (readBuffer.position() > 0) {
                    break;
                }
            }
            if (readBuffer.position() > 0) {
                executor.execute(new Consumer(this));
            }
        } finally {
            lock.unlock();
        }
    }

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }

    Queue<HttpRequest> getRequestQueue() {
        return requestQueue;
    }

    public HttpResponseDecoder getDecoder() {
        return decoder;
    }

    private static final sun.misc.Unsafe UNSAFE;
    private static final long connectionOffset;
    private static final long waitersOffset;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);

            Class<?> k = SimpleHttpClient.class;
            connectionOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("connection"));
            waitersOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }


}
