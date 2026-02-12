package com.xxl.job.core.server;

import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.constant.Const;
import com.xxl.job.core.openapi.ExecutorBiz;
import com.xxl.job.core.openapi.impl.ExecutorBizImpl;
import com.xxl.job.core.openapi.model.*;
import com.xxl.job.core.thread.ExecutorRegistryThread;
import com.xxl.tool.exception.ThrowableTool;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.response.Response;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

/**
 * Embedded Netty HTTP server for executor RPC communication.
 *
 * <p>This server provides HTTP endpoints for admin-to-executor communication:
 *
 * <ul>
 *   <li>/beat - Heartbeat check
 *   <li>/idleBeat - Check if job is idle (not running)
 *   <li>/run - Trigger job execution
 *   <li>/kill - Kill running job
 *   <li>/log - Retrieve job execution log
 * </ul>
 *
 * <p>Architecture:
 *
 * <ul>
 *   <li>Boss group: Accepts incoming connections (NIO event loop)
 *   <li>Worker group: Handles I/O operations (NIO event loop)
 *   <li>Business thread pool: Executes RPC method calls (200 core threads, 2000 queue capacity)
 *   <li>Idle connection detection: Closes connections idle for 90 seconds (3x heartbeat interval)
 * </ul>
 *
 * <p>Copied from: https://github.com/xuxueli/xxl-rpc
 *
 * @author xuxueli 2020-04-11 21:25
 */
public class EmbedServer {
    private static final Logger logger = LoggerFactory.getLogger(EmbedServer.class);

    /** Maximum HTTP request body size (5MB) */
    private static final int MAX_CONTENT_LENGTH = 5 * 1024 * 1024;

    /** Idle timeout in seconds (3x heartbeat interval of 30s) */
    private static final int IDLE_TIMEOUT_SECONDS = 30 * 3;

    /** Business thread pool configuration */
    private static final int BIZ_CORE_POOL_SIZE = 0;

    private static final int BIZ_MAX_POOL_SIZE = 200;
    private static final int BIZ_QUEUE_CAPACITY = 2000;
    private static final long BIZ_THREAD_KEEP_ALIVE_SECONDS = 60L;

    private ExecutorBiz executorBiz;
    private Thread thread;

    /**
     * Starts the embedded Netty server and executor registry.
     *
     * @param address executor address (IP:PORT format)
     * @param port listening port
     * @param appname executor application name
     * @param accessToken access token for authentication (optional)
     */
    public void start(
            final String address, final int port, final String appname, final String accessToken) {
        executorBiz = new ExecutorBizImpl();
        thread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                EventLoopGroup bossGroup = new NioEventLoopGroup();
                                EventLoopGroup workerGroup = new NioEventLoopGroup();
                                ThreadPoolExecutor bizThreadPool = createBusinessThreadPool();
                                try {
                                    // Configure and start Netty server
                                    ServerBootstrap bootstrap = new ServerBootstrap();
                                    bootstrap
                                            .group(bossGroup, workerGroup)
                                            .channel(NioServerSocketChannel.class)
                                            .childHandler(
                                                    new ChannelInitializer<SocketChannel>() {
                                                        @Override
                                                        public void initChannel(
                                                                SocketChannel channel) {
                                                            channel.pipeline()
                                                                    .addLast(
                                                                            new IdleStateHandler(
                                                                                    0,
                                                                                    0,
                                                                                    IDLE_TIMEOUT_SECONDS,
                                                                                    TimeUnit
                                                                                            .SECONDS))
                                                                    .addLast(new HttpServerCodec())
                                                                    .addLast(
                                                                            new HttpObjectAggregator(
                                                                                    MAX_CONTENT_LENGTH))
                                                                    .addLast(
                                                                            new EmbedHttpServerHandler(
                                                                                    executorBiz,
                                                                                    accessToken,
                                                                                    bizThreadPool));
                                                        }
                                                    })
                                            .childOption(ChannelOption.SO_KEEPALIVE, true);

                                    // Bind port
                                    ChannelFuture future = bootstrap.bind(port).sync();

                                    logger.info(
                                            ">>>>>>>>>>> orth remoting server start success,"
                                                    + " nettype = {}, port = {}",
                                            EmbedServer.class.getName(),
                                            port);

                                    // Start executor registry (heartbeat thread)
                                    startRegistry(appname, address);

                                    // Wait until server socket is closed
                                    future.channel().closeFuture().sync();

                                } catch (InterruptedException e) {
                                    logger.info(">>>>>>>>>>> orth remoting server stop.");
                                } catch (Throwable e) {
                                    logger.error(">>>>>>>>>>> orth remoting server error.", e);
                                } finally {
                                    // Shutdown event loops
                                    try {
                                        workerGroup.shutdownGracefully();
                                        bossGroup.shutdownGracefully();
                                    } catch (Throwable e) {
                                        logger.error(e.getMessage(), e);
                                    }
                                }
                            }
                        });
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Stops the embedded server and registry.
     *
     * @throws Exception if stop fails
     */
    public void stop() throws Exception {
        // Destroy server thread
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }

        // Stop executor registry
        stopRegistry();
        logger.info(">>>>>>>>>>> orth remoting server destroy success.");
    }

    /**
     * Creates the business thread pool for handling RPC requests.
     *
     * @return configured thread pool executor
     */
    private ThreadPoolExecutor createBusinessThreadPool() {
        return new ThreadPoolExecutor(
                BIZ_CORE_POOL_SIZE,
                BIZ_MAX_POOL_SIZE,
                BIZ_THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(BIZ_QUEUE_CAPACITY),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "orth, EmbedServer bizThreadPool-" + r.hashCode());
                    }
                },
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        throw new RejectedExecutionException(
                                "orth, EmbedServer bizThreadPool is EXHAUSTED!");
                    }
                });
    }

    /**
     * Starts the executor registry thread (heartbeat).
     *
     * @param appname executor application name
     * @param address executor address
     */
    public void startRegistry(final String appname, final String address) {
        ExecutorRegistryThread.getInstance().start(appname, address);
    }

    /** Stops the executor registry thread. */
    public void stopRegistry() {
        ExecutorRegistryThread.getInstance().toStop();
    }

    /**
     * Netty HTTP server handler for processing RPC requests.
     *
     * <p>This handler:
     *
     * <ol>
     *   <li>Validates HTTP method (POST only)
     *   <li>Validates access token
     *   <li>Routes requests to appropriate {@link ExecutorBiz} methods
     *   <li>Returns JSON-encoded {@link Response} objects
     * </ol>
     *
     * <p>Copied from: https://github.com/xuxueli/xxl-rpc
     *
     * @author xuxueli 2015-11-24 22:25:15
     */
    public static class EmbedHttpServerHandler
            extends SimpleChannelInboundHandler<FullHttpRequest> {
        private static final Logger logger = LoggerFactory.getLogger(EmbedHttpServerHandler.class);

        private final ExecutorBiz executorBiz;
        private final String accessToken;
        private final ThreadPoolExecutor bizThreadPool;

        public EmbedHttpServerHandler(
                ExecutorBiz executorBiz, String accessToken, ThreadPoolExecutor bizThreadPool) {
            this.executorBiz = executorBiz;
            this.accessToken = accessToken;
            this.bizThreadPool = bizThreadPool;
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, FullHttpRequest msg) {
            // Parse HTTP request
            String requestData = msg.content().toString(CharsetUtil.UTF_8);
            String uri = msg.uri();
            HttpMethod httpMethod = msg.method();
            boolean keepAlive = HttpUtil.isKeepAlive(msg);
            String accessTokenReq = msg.headers().get(Const.ORTH_ACCESS_TOKEN);

            // Execute in business thread pool
            bizThreadPool.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            // Dispatch request to ExecutorBiz
                            Object responseObj =
                                    dispatchRequest(httpMethod, uri, requestData, accessTokenReq);

                            // Serialize response to JSON
                            String responseJson = GsonTool.toJson(responseObj);

                            // Write HTTP response
                            writeResponse(ctx, keepAlive, responseJson);
                        }
                    });
        }

        /**
         * Dispatches HTTP request to appropriate {@link ExecutorBiz} method.
         *
         * @param httpMethod HTTP method
         * @param uri request URI
         * @param requestData request body (JSON)
         * @param accessTokenReq access token from request header
         * @return response object (typically {@link Response})
         */
        private Object dispatchRequest(
                HttpMethod httpMethod, String uri, String requestData, String accessTokenReq) {
            // Validate HTTP method
            if (HttpMethod.POST != httpMethod) {
                return Response.ofFail("Invalid request, HttpMethod not supported.");
            }

            // Validate URI
            if (uri == null || uri.trim().isEmpty()) {
                return Response.ofFail("Invalid request, URI-mapping empty.");
            }

            // Validate access token
            if (accessToken != null
                    && !accessToken.trim().isEmpty()
                    && !accessToken.equals(accessTokenReq)) {
                return Response.ofFail("The access token is wrong.");
            }

            // Route to ExecutorBiz methods
            try {
                switch (uri) {
                    case "/beat":
                        return executorBiz.beat();
                    case "/idleBeat":
                        IdleBeatRequest idleBeatParam =
                                GsonTool.fromJson(requestData, IdleBeatRequest.class);
                        return executorBiz.idleBeat(idleBeatParam);
                    case "/run":
                        TriggerRequest triggerParam =
                                GsonTool.fromJson(requestData, TriggerRequest.class);
                        return executorBiz.run(triggerParam);
                    case "/kill":
                        KillRequest killParam = GsonTool.fromJson(requestData, KillRequest.class);
                        return executorBiz.kill(killParam);
                    case "/log":
                        LogRequest logParam = GsonTool.fromJson(requestData, LogRequest.class);
                        return executorBiz.log(logParam);
                    default:
                        return Response.ofFail(
                                "Invalid request, URI-mapping(" + uri + ") not found.");
                }
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
                return Response.ofFail("Request error: " + ThrowableTool.toString(e));
            }
        }

        /**
         * Writes HTTP response to client.
         *
         * @param ctx channel handler context
         * @param keepAlive whether to keep connection alive
         * @param responseJson JSON response body
         */
        private void writeResponse(
                ChannelHandlerContext ctx, boolean keepAlive, String responseJson) {
            FullHttpResponse response =
                    new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.OK,
                            Unpooled.copiedBuffer(responseJson, CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");
            response.headers()
                    .set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.writeAndFlush(response);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error(">>>>>>>>>>> orth provider netty_http server caught exception", cause);
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                // Close idle connections
                ctx.channel().close();
                logger.debug(">>>>>>>>>>> orth provider netty_http server close an idle channel.");
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }
    }
}
