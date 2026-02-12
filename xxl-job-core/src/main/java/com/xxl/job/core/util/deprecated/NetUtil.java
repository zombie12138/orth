package com.xxl.job.core.util.deprecated;

import java.io.IOException;
import java.net.ServerSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deprecated network port availability utilities.
 *
 * <p>This utility class provided methods to check if a TCP port is in use and find an available
 * port by attempting to bind ServerSocket. Used primarily for dynamic port allocation.
 *
 * @deprecated This utility is deprecated and will be removed in a future version. Use the xxl-tool
 *     library's network utilities (com.xxl.tool.net.NetUtil) instead, which provide more robust
 *     port checking and availability detection with better error handling.
 * @author xuxueli 2017-11-29 17:00:25
 */
@Deprecated
public class NetUtil {
    private static Logger logger = LoggerFactory.getLogger(NetUtil.class);

    /**
     * find available port
     *
     * @param defaultPort
     * @return
     */
    public static int findAvailablePort(int defaultPort) {
        int portTmp = defaultPort;
        while (portTmp < 65535) {
            if (!isPortUsed(portTmp)) {
                return portTmp;
            } else {
                portTmp++;
            }
        }
        portTmp = defaultPort - 1;
        while (portTmp > 0) {
            if (!isPortUsed(portTmp)) {
                return portTmp;
            } else {
                portTmp--;
            }
        }
        throw new RuntimeException("no available port.");
    }

    /**
     * check port used
     *
     * @param port
     * @return
     */
    public static boolean isPortUsed(int port) {
        boolean used = false;
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            used = false;
        } catch (IOException e) {
            logger.info(">>>>>>>>>>> orth, port[{}] is in use.", port);
            used = true;
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    logger.info("");
                }
            }
        }
        return used;
    }
}
