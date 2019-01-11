package org.modraedlau.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IO event poller
 *
 * @author modraedlau
 */
public class EventLoop {
    private static Logger logger = LoggerFactory.getLogger(EventLoop.class);

    private final Selector selector;

    private final Thread selectThread;

    private final List<SimpleHttpClient> clients;

    private final ExecutorService executor;

    public EventLoop() throws IOException {
        selector = Selector.open();
        clients = new ArrayList<>();

        // Read and write task thread pool
        executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2,
            200,
            100L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10000),
            defaultThreadFactory());

        // Selector thread
        selectThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 阻塞
                    selector.select();

                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> it = keys.iterator();

                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();

                        final SimpleHttpClient client = (SimpleHttpClient) key.attachment();

                        try {
                            if (key.isConnectable()) {
                                if (client.getSocketChannel().finishConnect()) {
                                    // 连接成功
                                    client.connectedState();
                                    client.notifyConnected();
                                }
                            }

                            if (key.isReadable()) {
                                // 执行读取线程
                                client.executeConsumer();
                            }

                        } catch (IOException e) {
                            logger.error("Error reading", e);

                            // 关闭连接，并清除请求队列
                            key.cancel();
                            client.closeByException(e);
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Error selecting", e);
            } finally {
                try {
                    selector.close();
                } catch (IOException e) {
                    logger.error("Error closing selector", e);
                } finally {
                    logger.debug("Selector closed!");
                }
            }
        }, "selector");
    }

    public void register(SimpleHttpClient client) throws ClosedChannelException {
        clients.add(client);
        // 注册选择器
        client.getSocketChannel().register(selector,
            SelectionKey.OP_CONNECT | SelectionKey.OP_READ, client);
        client.setExecutor(executor);
    }

    public void start() {
        selectThread.start();
    }

    public void stop() {
        selectThread.interrupt();
        for (SimpleHttpClient client : clients) {
            client.disconnectedState();
        }
        executor.shutdown();
    }

    private static ThreadFactory defaultThreadFactory() {
        return new DefaultThreadFactory();
    }

    /**
     * Default thread factory
     */
    static class DefaultThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
            namePrefix = "rw-task-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
