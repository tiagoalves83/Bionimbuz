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
package br.unb.cic.bionimbus.services.discovery;

import br.unb.cic.bionimbus.config.BioNimbusConfig;
import br.unb.cic.bionimbus.plugin.PluginInfo;
import br.unb.cic.bionimbus.plugin.linux.LinuxGetInfo;
import br.unb.cic.bionimbus.plugin.linux.LinuxPlugin;
import br.unb.cic.bionimbus.services.AbstractBioService;
import br.unb.cic.bionimbus.services.messaging.CloudMessageService;
import br.unb.cic.bionimbus.services.messaging.CuratorMessageService.Path;
import br.unb.cic.bionimbus.toSort.Listeners;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.zookeeper.WatchedEvent;

@Singleton
public class DiscoveryService extends AbstractBioService {

    private static final int PERIOD_SECS = 10;
    private final ScheduledExecutorService schedExecService;

    @Inject
    public DiscoveryService(final CloudMessageService cms) {

        Preconditions.checkNotNull(cms);
        this.cms = cms;

        schedExecService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("DiscoveryService-%d")
                .build());
    }

    @Override
    public void run() {
        setDatasPluginInfo(false);
        /**
         * TODO: substituir por Guava Cache com expiração
         */

    }

    public void setDatasPluginInfo(boolean start) {
        try {

            LinuxGetInfo getinfo = new LinuxGetInfo();
            PluginInfo infopc = getinfo.call();

            infopc.setId(config.getId());
            infopc.setCostPerHour(config.getCost());
            
            if(start){
            // LinuxPlugin está contido nesse metodo, e deveria ser mandado 
            // para o linuxplugin Bionimbus.java
                LinuxPlugin linuxPlugin = new LinuxPlugin(config);

                infopc.setHost(config.getHost());

// Update uptime information to origin from zookeeper ---------------------------------------------------------------------------
                //infopc.setUptime(p2p.getPeerNode().uptime());
                infopc.setPrivateCloud(config.getPrivateCloud());

                //definindo myInfo após a primeira leitura dos dados
                linuxPlugin.setMyInfo(infopc);
                listeners.add(linuxPlugin);
            }else{
                String data = cms.getData(Path.NODE_PEER.getFullPath(infopc.getId()), null);
                if (data == null || data.trim().isEmpty()){
                    System.out.println("znode vazio para path " + Path.NODE_PEER.getFullPath(infopc.getId()));
                    return;
                }
                    
                PluginInfo plugin = new ObjectMapper().readValue(data, PluginInfo.class);
                plugin.setFsFreeSize(infopc.getFsFreeSize());
                plugin.setMemoryFree(infopc.getMemoryFree());
                plugin.setNumOccupied(infopc.getNumOccupied());
                infopc.setUptime(plugin.getUptime());
                infopc = plugin;
            }
            //armazenando dados do plugin no zookeeper
            cms.setData(Path.NODE_PEER.getFullPath(infopc.getId()), infopc.toString());
            
        } catch (IOException ex) {
            Logger.getLogger(DiscoveryService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void start(BioNimbusConfig config, List<Listeners> listeners) {
        try {
            Preconditions.checkNotNull(listeners);
            this.config = config;
            this.listeners = listeners;

            setDatasPluginInfo(true);

            listeners.add(this);

            schedExecService.scheduleAtFixedRate(this, 0, PERIOD_SECS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            Logger.getLogger(DiscoveryService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void getStatus() {
    }

    /**
     * Trata os watchers enviados da implementação da classe Watcher que recebe
     * uma notificação do zookeeper
     *
     * @param eventType evento recebido do zookeeper
     */
    @Override
    public void event(WatchedEvent eventType) {

    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void verifyPlugins() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
