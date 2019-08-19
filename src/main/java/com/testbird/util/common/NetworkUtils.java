package com.testbird.util.common;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetworkUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkUtils.class);

    private static final String ENV_NET_INTERFACE_NAME = "TB_NET_INTERFACE_NAME";
    private static InetAddress address = null;
    private static String MAC_ADDRESS = null;
    private static String IP_ADDRESS = null;

    public static String getIPAddress() {
        if (StringUtils.isEmpty(IP_ADDRESS)) {
            InetAddress address = getAddress();
            if (address != null) {
                IP_ADDRESS = address.getHostAddress();
                LOGGER.info("get ip address: {}", IP_ADDRESS);
            }
        }
        return IP_ADDRESS;
    }

    public static String getMacAddress() {
        if (StringUtils.isEmpty(MAC_ADDRESS)) {
            InetAddress address = getAddress();
            if (address != null) {
                try {
                    NetworkInterface networkInterface = NetworkInterface.getByInetAddress(address);
                    byte[] macByte = networkInterface.getHardwareAddress();
                    if (macByte != null && macByte.length > 1) {
                        MAC_ADDRESS = (parseByte(macByte[0]) + ":" + parseByte(macByte[1]) + ":" +
                                parseByte(macByte[2]) + ":" + parseByte(macByte[3]) + ":" +
                                parseByte(macByte[4]) + ":" + parseByte(macByte[5])).toUpperCase();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return MAC_ADDRESS;
    }

    public static String getLocalHostName() {
        try {
            return getAddress().getHostName();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }

    private static InetAddress getAddress() {
        if (address == null) {
            String name = System.getenv(ENV_NET_INTERFACE_NAME);
            LOGGER.info("env [{}]: {}", ENV_NET_INTERFACE_NAME, name);
            if (StringUtils.isNotEmpty(name)) {
                try {
                    NetworkInterface networkInterface = NetworkInterface.getByName(name);
                    if (networkInterface == null) {
                        LOGGER.error("networkInterface is null");
                        noticeInterface();
                        return address;
                    }
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        address = addresses.nextElement();
                        LOGGER.info("check: {}", address);
                        if (address instanceof Inet4Address) {
                            LOGGER.info("get address: {}", address);
                            break;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("getInetAddresses exception: ", e);
                }
            } else {
                try {
                    address = InetAddress.getLocalHost();
                } catch (Exception e) {
                    LOGGER.error("getLocalHost exception: ", e);
                }
            }
        }
        return address;
    }

    private static String parseByte(byte b) {
        int intValue;
        if (b >= 0) {
            intValue = b;
        } else {
            intValue = 256 + b;
        }
        return Integer.toHexString(intValue);
    }


    private static void noticeInterface() {
        Enumeration<NetworkInterface> netInterfaces = null;
        try {
            netInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            LOGGER.error("noticeInterface: ", e);
            return;
        }
        LOGGER.info("****** please check the interface is in these host interfaces below ******");
        while (netInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = netInterfaces.nextElement();

            LOGGER.info("name: {}, displayName: {}", networkInterface.getName(), networkInterface.getDisplayName());
        }
        LOGGER.info("****** please check the interface is in these host interfaces above ******");
    }

}
