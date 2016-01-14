/*
    BioNimbuZ is a federated cloud platform.
    Copyright (C) 2012-2015 Laboratory of Bioinformatics and Data (LaBiD), 
    Department of Computer Science, University of Brasilia, Brazil

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package br.unb.cic.bionimbus.config;

import br.unb.cic.bionimbus.p2p.Host;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import org.codehaus.jackson.annotate.JsonIgnore;

@Singleton
public class BioNimbusConfig {

//    @JsonIgnore
    private String id;

    @JsonIgnore
    private String infra;

    @JsonIgnore
    private String address;

    @JsonIgnore
    private String plugin;

    //retirar seeds
    @JsonIgnore
    private Set<Host> seeds = new HashSet<Host>();

    @JsonIgnore
    private boolean client = false;

    @JsonProperty("private_cloud")
    private int privateCloud;

    @JsonProperty("rpc_protocol")
    private String rpcProtocol;

    @JsonProperty("rpc_port")
    private Integer rpcPort;

    @JsonProperty("cost_per_giga")
    private double costpergiga;

    @JsonProperty("zookeeper_hosts")
    private String zkHosts;
    //retirar Host
    private Host host;

    @JsonProperty("server-path")
    private String serverPath = "";
    
    @JsonProperty("cost")
    private Double cost;
    
    private String proxyHost = "localhost";
    private int proxyPort = 8080;

    public void setRpcProtocol(String rpcProtocol) {
        this.rpcProtocol = rpcProtocol;
    }

    public String getRpcProtocol() {
        return rpcProtocol;
    }

    public Integer getRpcPort() {
        return rpcPort;
    }

    public void setRpcPort(Integer rpcPort) {
        this.rpcPort = rpcPort;
    }

    public String getZkHosts() {
        return zkHosts;
    }

    public void setZkConnString(String zkHosts) {
        this.zkHosts = zkHosts;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getInfra() {
        return infra;
    }

    public void setInfra(String infra) {
        this.infra = infra;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public Host getHost() {
        return host;
    }

    public boolean isClient() {
        return client;
    }

    public void setClient(boolean client) {
        this.client = client;
    }

    public void setSeeds(Set<Host> seeds) {
        this.seeds = seeds;
    }

    public Set<Host> getSeeds() {
        return seeds;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public double getCostPerGiga() {
        return costpergiga;
    }

    public void setCostPerGiga(double costpergiga) {
        this.costpergiga = costpergiga;
    }

    public String getServerPath() {
        return serverPath;
    }

    public void setServerPath(String serverPath) {
        this.serverPath = serverPath;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPlugin() {
        return plugin;
    }

    public void setPlugin(String plugin) {
        this.plugin = plugin;
    }

    public void setZkHosts(String zkHosts) {
        this.zkHosts = zkHosts;
    }

    public int getPrivateCloud() {
        return privateCloud;
    }

    public void setPrivateCloud(int privateCloud) {
        this.privateCloud = privateCloud;
    }
    
    public Double getCost() {
        return cost;
    }

    public void SetCost(Double cost) {
        this.cost = cost;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                //                .add("id", id)
                .add("rpc-protocol", rpcProtocol)
                .add("rpc-port", rpcPort)
                .add("zkHosts", zkHosts)
                .add("client", client)
                .add("host", host)
                .add("seeds", seeds)
                .add("private_cloud", privateCloud)
                .add("cost_per_giga", costpergiga)
                .add("server-path", serverPath)
                .add("cost", cost)
                .toString();
    }

}
