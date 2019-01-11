High-performance, non-blocking, simple java http client.

Quick Start
--------
```java
EventLoop eventLoop = new EventLoop();
SimpleHttpClient client1 = new SimpleHttpClient("http://www.baidu.com");
SimpleHttpClient client2 = new SimpleHttpClient("http://www.sina.com.cn");
eventLoop.register(client1);
eventLoop.register(client2);
eventLoop.start();

client1.get("/").thenAccept(response -> {
    System.out.println("------------server: baidu, path: /------------");
    System.out.println("status: " + response.getStatus());
    System.out.println("headers: " + response.getHeaders());
    System.out.println("body: " + response.getBody());
    System.out.println("----------------------------------------------");
});

client1.get("/more").thenAccept(response -> {
    System.out.println("------------server: baidu, path: /more------------");
    System.out.println("status: " + response.getStatus());
    System.out.println("headers: " + response.getHeaders());
    System.out.println("body: " + response.getBody());
    System.out.println("--------------------------------------------------");
});

client2.get("/").thenAccept(response -> {
    System.out.println("------------server: sina, path: /------------");
    System.out.println("status: " + response.getStatus());
    System.out.println("headers: " + response.getHeaders());
    System.out.println("body: " + response.getBody());
    System.out.println("----------------------------------------------");
});
```