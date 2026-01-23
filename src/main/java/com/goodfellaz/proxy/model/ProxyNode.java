package com.goodfellaz.proxy.model;

public class ProxyNode {
    private final String ip;
    private final int port;
    private final String region;
    private ProxyStatus status;
    private int currentLoad;

    public ProxyNode(String ip, int port, String region, ProxyStatus status, int currentLoad) {
        this.ip = ip;
        this.port = port;
        this.region = region;
        this.status = status;
        this.currentLoad = currentLoad;
    }

    public String getIp() { return ip; }
    public int getPort() { return port; }
    public String getRegion() { return region; }
    public ProxyStatus getStatus() { return status; }
    public void setStatus(ProxyStatus status) { this.status = status; }
    public int getCurrentLoad() { return currentLoad; }
    public void setCurrentLoad(int currentLoad) { this.currentLoad = currentLoad; }
}
