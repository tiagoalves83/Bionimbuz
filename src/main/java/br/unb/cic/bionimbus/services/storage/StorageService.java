package br.unb.cic.bionimbus.services.storage;

import br.unb.cic.bionimbus.avro.gen.FileInfo;
import br.unb.cic.bionimbus.avro.gen.NodeInfo;
import br.unb.cic.bionimbus.avro.rpc.AvroClient;
import br.unb.cic.bionimbus.avro.rpc.RpcClient;
import br.unb.cic.bionimbus.config.BioNimbusConfig;
import br.unb.cic.bionimbus.plugin.PluginFile;
import br.unb.cic.bionimbus.plugin.PluginInfo;
import br.unb.cic.bionimbus.security.Hash;
import br.unb.cic.bionimbus.security.Integrity;
import br.unb.cic.bionimbus.services.AbstractBioService;
import br.unb.cic.bionimbus.services.UpdatePeerData;
import br.unb.cic.bionimbus.services.messaging.CloudMessageService;
import br.unb.cic.bionimbus.services.messaging.CuratorMessageService;
import br.unb.cic.bionimbus.services.sched.SchedService;
import br.unb.cic.bionimbus.toSort.Listeners;
import br.unb.cic.bionimbus.utils.Nmap;
import br.unb.cic.bionimbus.utils.Put;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import static java.util.Objects.hash;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.avro.AvroRemoteException;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.codehaus.jackson.map.ObjectMapper;

@Singleton
public class StorageService extends AbstractBioService {
    
    @Inject
    private final MetricRegistry metricRegistry;
    private final ScheduledExecutorService executorService = Executors
            .newScheduledThreadPool(1, new BasicThreadFactory.Builder()
                    .namingPattern("StorageService-%d").build());
    private Map<String, PluginInfo> cloudMap = new ConcurrentHashMap<String, PluginInfo>();
    private final Map<String, PluginFile> savedFiles = new ConcurrentHashMap<String, PluginFile>();
//    private Set<String> pendingSaveFiles = new HashSet<String>();
    private final File dataFolder = new File("data-folder"); //TODO: remover hard-coded e colocar em node.yaml e injetar em StorageService
    private final Double MAXCAPACITY = 0.9;
    private final int PORT = 8080;
    private final int REPLICATIONFACTOR = 2;
    private final List<String> listFile = new ArrayList<String>();
    
    @Inject
    public StorageService(final CloudMessageService cms, MetricRegistry metricRegistry) {
        
        Preconditions.checkNotNull(cms);
        this.cms = cms;
        
        this.metricRegistry = metricRegistry;
    }
    
    @Override
    public void run() {        
    }
    
    /**
     * Método que inicia a storage
     * @param config
     * @param listeners
     */
    @Override
    public void start(BioNimbusConfig config, List<Listeners> listeners) {
        this.config = config;
        this.listeners = listeners;
        if (listeners != null) {
            listeners.add(this);
        }
        //Criando pastas zookeeper para o módulo de armazenamento
        if (!cms.getZNodeExist(CuratorMessageService.Path.PENDING_SAVE.toString(), false))
            cms.createZNode(CreateMode.PERSISTENT, CuratorMessageService.Path.PENDING_SAVE.toString(), null);
        if (!cms.getZNodeExist(CuratorMessageService.Path.FILES.getFullPath(config.getId(), "", ""), false))
            cms.createZNode(CreateMode.PERSISTENT, CuratorMessageService.Path.FILES.getFullPath(config.getId(), "", ""), "");

        //watcher para verificar se um pending_save foi lançado
        cms.getChildren(CuratorMessageService.Path.PENDING_SAVE.getFullPath("", "", ""), new UpdatePeerData(cms, this));
        cms.getChildren(CuratorMessageService.Path.PEERS.getFullPath("", "", ""), new UpdatePeerData(cms, this));

        //NECESSARIO atualizar a lista de arquivo local , a lista do zookeeper com os arquivos locais.
        checkFiles();
        checkPeers();
        try {
            if (getPeers().size() != 1) {
                checkReplicationFiles();
            }
        } catch (Exception ex) {
            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
        }
        executorService.scheduleAtFixedRate(this, 0, 3, TimeUnit.SECONDS);
    }
    
    @Override
    public void shutdown() {
        listeners.remove(this);
        executorService.shutdownNow();
    }
    
    @Override
    public void getStatus() {
        // TODO Auto-generated method stub
    }
    
    /**
     * Verifica os peers(plugins) existentes e adiciona um observador(watcher)
     * no zNode STATUS de cada plugin.
     */
    public void checkPeers() {
        for (PluginInfo plugin : getPeers().values()) {
            if(cms.getZNodeExist(CuratorMessageService.Path.STATUS.getFullPath(plugin.getId(), null, null), false))
                cms.getData(CuratorMessageService.Path.STATUS.getFullPath(plugin.getId(), null, null), new UpdatePeerData(cms, this));
        }
    }
    
    /**
     * Verifica os arquivos que existem no recurso.
     */
    public void checkFiles() {
        try {
            if (!dataFolder.exists()) {
//                System.out.println(" (CheckFiles) dataFolder " + dataFolder + " doesn't exists, creating...");
                dataFolder.mkdirs();
            }
            cms.getChildren(CuratorMessageService.Path.FILES.getFullPath(config.getId(), "", ""), new UpdatePeerData(cms, this));
            for (File file : dataFolder.listFiles()) {
                if (!savedFiles.containsKey(file.getName())) {
                    
                    PluginFile pluginFile = new PluginFile();
                    pluginFile.setId(file.getName());
                    pluginFile.setName(file.getName());
                    pluginFile.setPath(file.getPath());
                    
                    List<String> listIds = new ArrayList<String>();
                    listIds.add(config.getId());
                    
                    pluginFile.setPluginId(listIds);
                    pluginFile.setSize(file.length());
                    //cria um novo znode para o arquivo e adiciona o watcher
                    cms.createZNode(CreateMode.PERSISTENT, CuratorMessageService.Path.PREFIX_FILE.getFullPath(config.getId(), pluginFile.getId(), ""), pluginFile.toString());
                    cms.getData(CuratorMessageService.Path.PREFIX_FILE.getFullPath(config.getId(), pluginFile.getId(), ""), new UpdatePeerData(cms, this));
                    
                    savedFiles.put(pluginFile.getName(), pluginFile);
                }
                
            }
        } catch (Exception ex) {
            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
      /**
     * Retorna o hash de um arquivo. Usado para verificar a integridade do arquivo.
     * @param fileName
     * @return 
     * @throws java.security.NoSuchAlgorithmException 
     * @throws java.io.IOException 
     */
    public String getFileHash(String fileName) throws NoSuchAlgorithmException, IOException {
         String rootPath = "/home/zoonimbus/zoonimbusProject/data-folder/"; 
         //Produz o hash do arquivo
         String hashFile = Hash.SHA1File(rootPath + fileName);
         return  hashFile;
    }
    
    /**
     * Checa quantas cópias existem de um arquivo, caso existam menos cópias do
     * que REPLICATIONFACTOR inicia a replicação deste arquivo; Este método
     * checa todos os arquivos da federação.
     *
     * @throws Exception
     */
    public void checkReplicationFiles() throws Exception {
        for (Collection<String> collection : getFiles().values()) {
            /*
             * Percorre cada arquivo e o IP que possui ele
             */
            for (String fileNamePlugin : collection) {
                if (!existReplication(fileNamePlugin)) {
                    /*
                    * Caso não exista um número de cópias igual a REPLICATIONFACTOR inicia as cópias,
                    * enviando uma RPC para o peer que possui o arquivo, para que ele replique.
                    */
                    String ipPluginFile = getIpContainsFile(fileNamePlugin);
                    if (!ipPluginFile.isEmpty() && !ipPluginFile.equals(config.getAddress())) {
                        RpcClient rpcClient = new AvroClient("http", ipPluginFile, PORT);
                        rpcClient.getProxy().notifyReply(fileNamePlugin, ipPluginFile);
                        rpcClient.close();
                    } else {
                        replication(fileNamePlugin, ipPluginFile);
                    }
                }
            }
        }
    }
    
    /**
     * Verifica a existência da replicação do arquivo na federação. Se a
     * replicação estiver feita retona true; Fator de replicação igual a 2;
     * Retorna true se existir replicação.
     */
    private boolean existReplication(String fileName) throws IOException {
        int cont = 0;
        //System.out.println("(existReplication)Verificando se o arquivo: "+fileName+" está replicado!");
        for (Collection<String> collection : getFiles().values()) {
            for (String fileNamePlugin : collection) {
                if (fileName.equals(fileNamePlugin)) {
                    cont++;
                }
            }
        }
        //System.out.println("(existReplication) Arquivo: "+fileName+" contém: "+ cont+" replicas!");
        if (cont < REPLICATIONFACTOR) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Cria map com endereço dos peers(plugins) e seus respectivos arquivos
     * baseado nos dados do zookeeper.
     *
     * @return map de endereço e lista de arquivos.
     * @throws java.io.IOException
     */
    public Map<String, List<String>> getFiles() throws IOException {
        Map<String, List<String>> mapFiles = new HashMap<String, List<String>>();
        List<String> listFiles;
        checkFiles();

        for (PluginInfo plugin : getPeers().values()) {
            listFiles = new ArrayList<String>();
            for (String file : cms.getChildren(plugin.getPath_zk() + CuratorMessageService.Path.FILES.toString(), new UpdatePeerData(cms, this))) {
                listFiles.add(file.substring(5, file.length()));
            }
            mapFiles.put(plugin.getHost().getAddress(), listFiles);
        }
        
        return mapFiles;
        
    }
    
    /**
     * Metodo para pegar o Ip de cada peer na federação e verificar em qual peer o
     * arquivo está, se o arquivo for encontrado retorna o Ip do
     * peer, caso contrário retorna null.
     *
     * @param file
     * @return Ip que possui o arquivo ou null
     * @throws java.io.IOException
     */
    public String getIpContainsFile(String file) throws IOException {
        List<String> listFiles;
        //NECESSARIO atualizar a lista de arquivo local , a lista do zookeeper com os arquivos locais. Não é feito em nenhum momento
        //caso não seja chamado a checkFiles();
        checkFiles();

        for (PluginInfo plugin : getPeers().values()) {
            listFiles = cms.getChildren(plugin.getPath_zk() + CuratorMessageService.Path.FILES.toString(), null);
            for (String checkfile : listFiles) {
                
                String idfile = checkfile.substring(checkfile.indexOf(CuratorMessageService.Path.UNDERSCORE.toString()) + 1);
                if (file.equals(idfile)) {
                    return plugin.getHost().getAddress();
                }
            }
        }
        return "";
    }
    
    /**
     * Retorna o tamanho do arquivo, dado o nome do mesmo.
     * @param file O nome do arquivo
     * @return O tamanho do arquivo
     */
    public long getFileSize(String file) {
        
        try {
            List<String> listFiles;
            for (PluginInfo plugin : getPeers().values()) {
                listFiles = cms.getChildren(plugin.getPath_zk() + CuratorMessageService.Path.FILES.toString(), null);
                PluginFile files = new ObjectMapper().readValue(cms.getData(CuratorMessageService.Path.PREFIX_FILE.getFullPath(plugin.getId(), file, ""), null), PluginFile.class);
                return files.getSize();
            }
        } catch (IOException ex) {
            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return 0;
    }
    
    /**
     * Recebe uma list com todos os peers da federação e seta o custo de
     * armazenamento em cada plugin
     *
     * @param list - Lista com todos os plugins da federação
     * @return - Lista com todos os plugins com seus custos de armazenamento
     * inseridos
     */
    public List<NodeInfo> bestNode(List<NodeInfo> list) {
        
        List<NodeInfo> plugins;
        cloudMap = getPeers();
        for (NodeInfo node : list) {
            cloudMap.get(node.getPeerId()).setLatency(node.getLatency());
            cloudMap.get(node.getPeerId()).setFsFreeSize(node.getFreesize());
        }
        StoragePolicy policy = new StoragePolicy();
        /*
        * Dentro da Storage Policy é feito o ordenamento da list de acordo com o custo de armazenamento
        */
        plugins = policy.calcBestCost(cms, cloudMap.values());
        
        return plugins;
    }
    
    /**
     * Verifica se um arquivo existe em um peer e seta o seu Znode no Zookeeper
     *
     * @param file - Arquivo a ser verifcado
     * @return true caso o arquivo exista e tenha sido setado
     */
    public boolean checkFilePeer(PluginFile file) {
        System.out.println("(checkFilePeer)vericando se o arquivo "+file.toString()+" existe no peer");
        String pathHome = System.getProperty("user.dir");
        String path =  (pathHome.substring(pathHome.length()).equals("/") ? pathHome+"data-folder/" : pathHome+"/data-folder/");
        File localFile = new File(path + file.getName());
        
        if (localFile.exists()) {
            cms.createZNode(CreateMode.PERSISTENT, cms.getPath().PREFIX_FILE.getFullPath(config.getId(), file.getId(), ""), file.toString());
            cms.getData(cms.getPath().PREFIX_FILE.getFullPath(config.getId(), file.getId(), ""), new UpdatePeerData(cms, this));
            return true;
        }
        System.out.println("\n\n arquivo nao encontrado no peer"+config.getId());
        return false;
    }
    
    /**
     * Método que manda o comando dizendo que o arquivo foi upado com o intuito de replicar esse arquivo pelos nós.
     *
     * @param fileUploaded
     * @throws KeeperException
     * @throws InterruptedException
     * @throws IOException    
     */
    public synchronized void fileUploaded(PluginFile fileUploaded) throws KeeperException, InterruptedException, IOException, NoSuchAlgorithmException {
        System.out.println("(fileUploaded) Checando se existe a requisição no pending saving"+fileUploaded.toString());
        if (cms.getZNodeExist(cms.getPath().PREFIX_PENDING_FILE.getFullPath("", fileUploaded.getId(), ""), false)) {
            
            String ipPluginFile;
            ipPluginFile = getIpContainsFile(fileUploaded.getName());
            FileInfo file = new FileInfo();
            file.setFileId(fileUploaded.getId());
            file.setName(fileUploaded.getName());
            file.setSize(fileUploaded.getSize());
            String idPluginFile=null;
            for(String idPlugin : fileUploaded.getPluginId()){
                idPluginFile=idPlugin;
                break;
            }
                        
            System.out.println("(FileUploaded)IdPluginFile: "+idPluginFile);
            //Verifica se a máquina que recebeu essa requisição não é a que está armazenando o arquivo
            if (!config.getAddress().equals(ipPluginFile)){
                  RpcClient rpcClient = new AvroClient("http", "164.41.209.89", PORT);
                  //RpcClient rpcClient = new AvroClient("http", ipPluginFile, PORT);
                  String filePeerHash = rpcClient.getProxy().getFileHash(fileUploaded.getName());
                  
                  //Verifica se o arquivo foi corretamente transferido ao nó. Só faz a verificação caso o arquivo não seja saída de uma execução.
                  Integrity integrity = new Integrity();
                  if(integrity.verifyFile(filePeerHash, fileUploaded.getHash())) {
                        try {
                            if (rpcClient.getProxy().verifyFile(file, fileUploaded.getPluginId())&&cms.getZNodeExist(CuratorMessageService.Path.PREFIX_FILE.getFullPath(idPluginFile,fileUploaded.getId(), ""), false)) {
                                rpcClient.getProxy().notifyReply(fileUploaded.getName(), ipPluginFile);                                
                                //Remova o arquivo do PENDING FILE já que ele foi upado
                                cms.delete(CuratorMessageService.Path.PREFIX_PENDING_FILE.getFullPath("", fileUploaded.getId(), ""));
                             }
                              rpcClient.close();
                         } catch (Exception ex) {                            
                             Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                         }
                  } else {
                    //TO-DO:Return to the Shell option!  
                  }                  
              }else {
                  if (checkFilePeer(fileUploaded)) {
                      String filePeerHash = getFileHash(fileUploaded.getName());
                      
                      //Verifica se o arquivo foi corretamente transferido ao nó. Só faz a verificação caso o arquivo não seja saída de uma execução.
                      Integrity integrity = new Integrity();
                      if(integrity.verifyFile(filePeerHash, fileUploaded.getHash())) {
                            if (cms.getZNodeExist(CuratorMessageService.Path.PREFIX_FILE.getFullPath(idPluginFile, fileUploaded.getId(), ""), true)&&!existReplication(file.getName())){
                                 try {
                                      replication(file.getName(), config.getAddress());
                                      if(existReplication(file.getName())){                                          
                                          //Remova o arquivo do PENDING FILE já que ele foi upado
                                          cms.delete(CuratorMessageService.Path.PREFIX_PENDING_FILE.getFullPath("", fileUploaded.getId(),""));
                                      } else {
                                          System.out.println("\n\n Erro na replicacao, arquivo nao foi replicado!!");
                                          }
                                  } catch (JSchException ex) {
                                          Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                                      } catch (SftpException ex) {
                                          Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                                      }
                          }
                    } else {
                          //TO-DO:Return to the Shell option! 
                      }
                  }
                }
        } else {
            System.out.println("Arquivo não encontrado nas pendências !");
        }
    }
    
    /**
     * Metodo que checa os znodes filhos da pending_save, para replica-lós
     * @throws java.io.IOException
     * @throws java.security.NoSuchAlgorithmException
     */
    public void checkingPendingSave() throws IOException, NoSuchAlgorithmException{
        
        ObjectMapper mapper = new ObjectMapper();
        Boolean validFile = true;
        Integrity integrity = new Integrity();
        int cont = 0;
        List<String> pendingSave = cms.getChildren(CuratorMessageService.Path.PENDING_SAVE.toString(), null);
        //pendingSaveFiles.addAll(pendingSave);
        for(String files: pendingSave){
            try {
                String data = cms.getData(CuratorMessageService.Path.PREFIX_PENDING_FILE.getFullPath("", files.substring(13, files.length()), ""), null);
                //Verifica se arquivo existe
                if (data == null || data.trim().isEmpty()){
                    System.out.println(">>>>>>>>>> NÃO EXISTEM DADOS PARA PATH " + CuratorMessageService.Path.PENDING_SAVE.getFullPath("", "", ""));
                    continue;
                }
                PluginFile fileplugin = mapper.readValue(data, PluginFile.class);
                
                //Verifica se é um arquivo de saída de uma execução e se o arquivo foi gerado nesse recurso
                if(fileplugin.getService()!=null && fileplugin.getService().equals(SchedService.class.getSimpleName()) && fileplugin.getPluginId().get(0).equals(config.getId())){
                    //Adiciona o arquivo a lista do zookeeper
                    checkFiles();
                }
                               
                if(validFile) {                
                    while(cont < 6){
                        if(fileplugin.getPluginId().size() == REPLICATIONFACTOR){
                            cms.delete(CuratorMessageService.Path.PENDING_SAVE.getFullPath("", fileplugin.getId(), ""));
                            break;
                        }                    
                        String address = getIpContainsFile(fileplugin.getName());
                        if(!address.isEmpty() && !address.equals(config.getAddress())){
                            RpcClient rpcClient = new AvroClient("http", address, PORT);
                            rpcClient.getProxy().notifyReply(fileplugin.getName(), address);
                            try {
                                rpcClient.close();
                                if(existReplication(fileplugin.getName())){
                                    cms.delete(CuratorMessageService.Path.PREFIX_PENDING_FILE.getFullPath("", fileplugin.getId(),""));
                                    break;
                                }
                                else{
                                    cont++;
                                }
                            } catch (Exception ex) {
                                Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } else{
                            try{
                                replication(fileplugin.getName(),address);
                            } catch (JSchException ex) {
                                Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (SftpException ex) {
                                Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            if(existReplication(fileplugin.getName())){
                                cms.delete(CuratorMessageService.Path.PREFIX_PENDING_FILE.getFullPath("", fileplugin.getId(),""));
                                break;
                            }
                            else{
                                cont++;
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
            }
            //verifica se exite replicação quando houver mais de um peer
            if (getPeers().size() != 1)
                existReplication(files);
        }
    }
    
    /**
     * Realiza a replicação de arquivos, sejam eles enviados pelo cliente ou
     * apenas gerados na própria federação
     *
     * @param filename - nome do arquivo
     * @param address - endereço do peer que possui o arquivo
     * @throws IOException
     * @throws JSchException
     * @throws SftpException
     */
    public synchronized void replication(String filename, String address) throws IOException, JSchException, SftpException {
        
        System.out.println("(replication) Replicando o arquivo de nome: "+filename+" do peer: "+address);
        List<NodeInfo> pluginList = new ArrayList<NodeInfo>();
        List<String> idsPluginsFile = new ArrayList<String>();
        String pathHome = System.getProperty("user.dir");
        String path =  (pathHome.substring(pathHome.length()).equals("/") ? pathHome+"data-folder/" : pathHome+"/data-folder/");
        File file = new File(path+ filename);
        
        
        int filesreplicated = 1;
        
        /*
        * Verifica se o arquivo existe no peer
        */
        if (file.exists()) {
            FileInfo info = new FileInfo();
            info.setFileId(file.getName());
            info.setName(file.getName());
            info.setSize(file.length());
            
            PluginFile pluginFile = new PluginFile(info);
            /*
            * PLuginList ira receber a lista dos Peers disponiveis na federação
            * e que possuem espaço em disco para receber o arquivo a ser replicado
            */
            pluginFile.setPath("data-folder/"+info.getName());
            NodeInfo no = null;
            
            /*
            * While para que o peer pegue o próprio endereço e ele seja removido da lista de peers,
            * isso é feito para evitar que ele tente replicar
            * o arquivo para ele mesmo.
            */
            for (NodeInfo node: getNodeDisp(info.getSize())){
                if(node.getAddress().equals(address)){
                    no=node;
                    break;
                }
            }
            if(no!=null){
                pluginList.remove(no);
                idsPluginsFile.add(config.getId());
                pluginList = new ArrayList<NodeInfo>(bestNode(pluginList));
                for (NodeInfo curr : pluginList){
                    if (no.getAddress().equals(curr.getAddress())){
                        no = curr;
                        break;
                    }
                }
                
                pluginList.remove(no);
            }
            pluginList = new ArrayList<NodeInfo>(bestNode(pluginList));
            pluginList.remove(no);
            Iterator<NodeInfo> bt = pluginList.iterator();
            while (bt.hasNext() && filesreplicated != REPLICATIONFACTOR) {
                NodeInfo node = (NodeInfo) bt.next();
                if (!(node.getAddress().equals(address))) {
                    /*
                    * Descoberto um peer disponivel, tenta enviar o arquivo
                    */
                    Put conexao = new Put(node.getAddress(), dataFolder + "/" + info.getName());
                    if (conexao.startSession()) {
                        idsPluginsFile.add(node.getPeerId());
                        
                        pluginFile.setPluginId(idsPluginsFile);
                        /*
                        * Com o arquivo enviado, seta os seus dados no Zookeeper
                        */
                        for (String idPlugin : idsPluginsFile) {
                            if (cms.getZNodeExist(CuratorMessageService.Path.PREFIX_FILE.getFullPath(idPlugin, filename, ""), true)) {
                                cms.setData(CuratorMessageService.Path.PREFIX_FILE.getFullPath(idPlugin, filename, ""), pluginFile.toString());
                            } else {
                                cms.createZNode(CreateMode.PERSISTENT, CuratorMessageService.Path.PREFIX_FILE.getFullPath(idPlugin, filename, ""), pluginFile.toString());
                            }
                            cms.getData(CuratorMessageService.Path.PREFIX_FILE.getFullPath(idPlugin, filename, ""), new UpdatePeerData(cms, this));
                        }
                        filesreplicated++;
                        if(filesreplicated==REPLICATIONFACTOR)
                            break;
                    }
                    
                }
            }
        }
    }
    
    
    /**
     * Pega uma lista com todos os peers da federação e separa eles de acordo
     * com o tamanho do arquivo, criando uma lista somente com os peers que
     * possuem condições de receber o arquivo
     *
     * @param lengthFile
     * @return - Lista com peers que podem receber o arquivo
     */
    public List<NodeInfo> getNodeDisp(long lengthFile) {
        List<NodeInfo> nodesdisp = new ArrayList<NodeInfo>();
        Collection<PluginInfo> cloudPlugin = getPeers().values();
        nodesdisp.clear();
        for (PluginInfo plugin : cloudPlugin) {
            try {
                NodeInfo node = new NodeInfo();
                
                if ((long) (plugin.getFsFreeSize() * MAXCAPACITY) > lengthFile && plugin.getId().equals(config.getId())) {
                    node.setLatency(Ping.calculo(plugin.getHost().getAddress()));
                    if(node.getLatency().equals(Double.MAX_VALUE))
                        node.setLatency(Nmap.nmap(plugin.getHost().getAddress()));
                    node.setAddress(plugin.getHost().getAddress());
                    node.setFreesize(plugin.getFsFreeSize());
                    node.setPeerId(plugin.getId());
                    nodesdisp.add(node);
                }
            } catch (IOException ex) {
                Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return nodesdisp;
    }
    
    /**
     * Seta no Zookeeper os dados de um arquivo que foi requisitado por um
     * cliente para ser submetido na federação
     *
     * @param file - Arquivo a ser submetido
     */
    public void setPendingFile(PluginFile file) {
        cms.createZNode(CreateMode.PERSISTENT, CuratorMessageService.Path.PREFIX_PENDING_FILE.getFullPath("", file.getId(), ""), file.toString());
    }
    
    /**
     * Cria uma Map com o ID de um peer e seus respectivos arquivos
     *
     * @param pluginId id do plugin para pegar os arquivos do plugin
     * @return Map com os plugins e seus arquivos
     */
    public List<PluginFile> getFilesPeer(String pluginId) {
        List<String> children;
        List<PluginFile> filesPeerSelected = new ArrayList<PluginFile>();
        //NECESSARIO atualizar a lista de arquivo local , a lista do zookeeper com os arquivos locais. Não é feito em nenhum momento
        //caso não seja chamado a checkFiles();
        checkFiles();
        try {
            children = cms.getChildren(CuratorMessageService.Path.FILES.getFullPath(pluginId, "", ""), null);
            for (String fileId : children) {
                String fileName = fileId.substring(5, fileId.length());
                ObjectMapper mapper = new ObjectMapper();
                PluginFile file = mapper.readValue(cms.getData(CuratorMessageService.Path.PREFIX_FILE.getFullPath(pluginId, fileName, ""), null), PluginFile.class);
                filesPeerSelected.add(file);
            }
        } catch (IOException ex) {
            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return filesPeerSelected;
    }
    
    @Override
    public void verifyPlugins() {
        Collection<PluginInfo> temp = getPeers().values();
        temp.removeAll(cloudMap.values());
        for (PluginInfo plugin : temp) {
            if(cms.getZNodeExist(CuratorMessageService.Path.STATUS.getFullPath(plugin.getId(), null, null), false))
                cms.getData(CuratorMessageService.Path.STATUS.getFullPath(plugin.getId(), "", ""), new UpdatePeerData(cms, this));
        }
    }
    
    /**
     * Método que recebe um evento do zookeeper caso os znodes setados nessa classe sofra alguma alteração, criado, deletado, modificado, trata os eventos de acordo com o tipo do mesmo
     * @param eventType
     */
    @Override
    public void event(WatchedEvent eventType) {
        String path = eventType.getPath();
        switch (eventType.getType()) {
            
            case NodeChildrenChanged:
                if (eventType.getPath().equals(CuratorMessageService.Path.PEERS.toString())) {
                    if (cloudMap.size() < getPeers().size()) {
                        verifyPlugins();
                    }
                }else if (eventType.getPath().equals(CuratorMessageService.Path.PENDING_SAVE.toString())) {
                    //chamada para checar a pending_save apenas quando uma alerta para ela for lançado
//                       try{
//                            checkingPendingSave();
//                        }
//                        catch (IOException ex) {
//                            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
//                        }
                }
                break;
            case NodeDeleted:
                if (eventType.getPath().contains(CuratorMessageService.Path.STATUS.toString())) {
                    System.out.println("StoringService : znode status apagada");
                    String peerId = path.substring(12, path.indexOf("/STATUS"));
                    if(getPeers().values().size()!=1){
                        try {
                            if (!cms.getZNodeExist(CuratorMessageService.Path.STATUSWAITING.getFullPath(peerId, "", ""), false)) {
                                cms.createZNode(CreateMode.PERSISTENT, CuratorMessageService.Path.STATUSWAITING.getFullPath(peerId, "", ""), "");
                            }
                            
                            StringBuilder info = new StringBuilder(cms.getData(CuratorMessageService.Path.STATUSWAITING.getFullPath(peerId, "", ""), null));
                            
                            //verifica se recurso já foi recuperado ou está sendo recuperado por outro recurso
                            if (!info.toString().contains("S") /*&& !info.toString().contains("L")*/) {
                                
                                //bloqueio para recuperar tarefas sem que outros recursos realizem a mesma operação
                                // cms.setData(cms.getPath().STATUSWAITING.getFullPath(peerId, "", ""), info.append("L").toString());
                                
                                //Verificar pluginid para gravar
                                for (PluginFile fileExcluded : getFilesPeer(peerId)) {
                                    String idPluginExcluded = null;
                                    for (String idPlugin : fileExcluded.getPluginId()) {
                                        if (peerId.equals(idPlugin)&&!idPlugin.equals(config.getId())) {
                                            idPluginExcluded = idPlugin;
                                            break;
                                        }
                                    }
                                    
                                    if (fileExcluded.getPluginId().size()>1)
                                        fileExcluded.getPluginId().remove(idPluginExcluded);
                                    
                                    setPendingFile(fileExcluded);
                                    fileExcluded.setService("storagePeerDown");
                                    fileUploaded(fileExcluded);
                                }
                                
                                //retira bloqueio de uso e adiciona marcação de recuperação
                                //    info.deleteCharAt(info.indexOf("L"));
                                info.append("S");
                                cms.setData(CuratorMessageService.Path.STATUSWAITING.getFullPath(peerId, "", ""), info.toString());
                                
                                //nao é necessário chamar esse método aqui, ele será chamado se for necessário ao receber um alerta de watcher
                                //                            checkingPendingSave();
                            }
                            
                        } catch (AvroRemoteException ex) {
                            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (KeeperException ex) {
                            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (NoSuchAlgorithmException ex) {
                            Logger.getLogger(StorageService.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    break;
                }
        }
    }
}
