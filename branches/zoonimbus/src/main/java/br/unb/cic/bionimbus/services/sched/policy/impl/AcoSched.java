/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package br.unb.cic.bionimbus.services.sched.policy.impl;

import br.unb.cic.bionimbus.client.JobInfo;
import br.unb.cic.bionimbus.plugin.PluginInfo;
import br.unb.cic.bionimbus.plugin.PluginTask;
import br.unb.cic.bionimbus.plugin.PluginTaskState;
import br.unb.cic.bionimbus.services.ZooKeeperService;
import br.unb.cic.bionimbus.services.sched.policy.SchedPolicy;
import br.unb.cic.bionimbus.utils.Pair;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

/**
 * @author gabriel
 */
public class AcoSched extends SchedPolicy {


    private HashMap<String, ArrayList<Double>> mapAcoDatas;
    private Double smallerProbability = Double.MAX_VALUE;
    private Map<PluginTask, Integer> blackList = new HashMap<PluginTask, Integer>();
    private static final int BLACKLIST_LIMIT = 12;
    private static final String DIR_MAPACODATAS = "ACO_DATAS";
    private static final String DIR_SIZEALLJOBS ="SIZE_JOBS";
    private ZooKeeperService zk;
   
    @Override
    public HashMap<JobInfo, PluginInfo> schedule(Collection<JobInfo> jobInfos,ZooKeeperService zk) {
        HashMap jobCloud = new HashMap<JobInfo, PluginInfo>();
        JobInfo biggerJob = getBiggerJob(new ArrayList<JobInfo>(jobInfos));
        
        // escalonador irá receber um zookeeperService como parametro
        this.zk = zk;
        
        jobCloud.put(biggerJob, scheduleJob(biggerJob));

        return jobCloud;

    }

    /**
     * Recebe um job a ser escalonado, envia o job para a execução do algoritmo ACO onde os recursos
     * irão ser classificados e retornados para que o melhor entre eles seja retornado.
     *
     * @param jobInfo contém as informações do job que deve ser escalonado
     * @return o melhor recurso disponível para executar o job informado
     */
    private PluginInfo scheduleJob(JobInfo jobInfo) {

//        List<PluginInfo> listServices = rankingServices(getBestClouds(jobInfo));
        List<PluginInfo> listServices = getExactClouds(jobInfo);
        
        //realiza a chamada do método para a leitura dos dados no servidor zookeeper
        mapAcoDatas = getMapAcoDatasZooKeeper(listServices);
        
        //verifica se existe mais de um plugin para rodar a tarefa, se não houver retorna o plugin existente        ***descomentar ao estar em rede com outros plugins
//        if(listServices.size()==1){                       
//            return listServices.iterator().next();
//        }
        
        //inicia o ACO para encontrar melhor PC dentro das nuvens escolhidas para o job
        AlgorithmAco(listServices);
       
        
//--->  //encontra o melhor PC e retorna o valor

        PluginInfo plugin = new PluginInfo();
        plugin.setRanking(Double.MIN_VALUE);

        for (PluginInfo plg : listServices) {
            if (plg.getRanking() > plugin.getRanking()) {
                plugin = plg;
            }
        }
        
        //chamada para metodo que atualiza o valor do feromonio de cada plugin de acordo com o melhor escolhido
        theBestPheromone(listServices, plugin.getRanking());
        //armazena as informações utilizadas e atualizadas para o escalonamento no servidor zookeeper
        setMapAcoDatasZooKeeper(listServices);
        //funcao para armazenar o tamanho do job que sera executado no plugin
        upDateSizeOfJobsSchedCloud(plugin,jobInfo);
        
        return plugin;
    }

    /**
     * Retorna as melhores nuvens para realizar o serviço, nuvens que rodam o tipo de serviço requerido e se devem ser privadas ou publicas.
     *
     * @param jobInfo
     * @return lista com as nuvens que rodam o serviço requrido
     */

    public List getExactClouds(JobInfo jobInfo) {
        //seleciona as nuvens disponíveis para o tipo informado
        List cloudList = filterTypeCloud(getCloudMap().values(), 2); //adiciona filtro se opção for apenas para nuvens públicas, opcao 2 para ambas

        //configurar o tipo de serviço requerido
        filterByService(jobInfo.getServiceId(), cloudList);

        //ordena as melhores nuvens que disponibilizam os serviços requeridos
        // quickSort(0, cloudList.size()-1, rankingServices(cloudList));

        return cloudList;

    }

    //Método igual do AHPPOLICY
    @Override
    public synchronized List<PluginTask> relocate(Collection<Pair<JobInfo, PluginTask>> taskPairs) {
        List<PluginTask> tasksToCancel = new ArrayList<PluginTask>();
        for (Pair<JobInfo, PluginTask> taskPair : taskPairs) {
            PluginTask task = taskPair.getSecond();
            JobInfo job = taskPair.getFirst();

            if (PluginTaskState.RUNNING.equals(task.getState())) {
                if (blackList.containsKey(task)) {
                    blackList.remove(task);
                }
            }

            if (!PluginTaskState.WAITING.equals(task.getState())) continue;

            int count = 0;
            if (blackList.containsKey(task)) {
                count = blackList.get(task);
            }

            blackList.put(task, count + 1);

            if (blackList.get(task) >= BLACKLIST_LIMIT) {
                if (job != null) {
                    tasksToCancel.add(task);
                }
            }
        }

        for (PluginTask task : tasksToCancel) {
            blackList.remove(task);
        }

        return tasksToCancel;
    }

    @Override
    public void cancelJobEvent(PluginTask task) {
        blackList.remove(task);
    }

    @Override
    public void jobDone(PluginTask task) {
        if (blackList.containsKey(task)) {
            blackList.remove(task);
        }
    }


    public static JobInfo getBiggerJob(List<JobInfo> jobInfos) {
        if (jobInfos.size() == 0)
            return null;

        JobInfo bigger = null;
        long biggerTotal = 0L;
        for (JobInfo jobInfo : jobInfos) {
            long total = getTotalSizeOfJobsFiles(jobInfo);
            if (bigger == null) {
                bigger = jobInfo;
                biggerTotal = total;
            } else {
                if (getTotalSizeOfJobsFiles(jobInfo) > biggerTotal) {
                    bigger = jobInfo;
                    biggerTotal = total;
                }
            }
        }
        return bigger;
    }

    public static Long getTotalSizeOfJobsFiles(JobInfo jobInfo) {
        long sum = 0;

        for (Pair<String, Long> pair : jobInfo.getInputs()) {
            sum += pair.second;
        }

        return sum;
    }
    
    public void upDateSizeOfJobsSchedCloud(PluginInfo plugin, JobInfo job){
        Double size = new Double(getDatasZookeeper(plugin, DIR_SIZEALLJOBS))+getTotalSizeOfJobsFiles(job).doubleValue();
        
        setDatasZookeeper(plugin, DIR_SIZEALLJOBS, size.toString());
        
    }
    
    /**
     * Seleciona o tipo de nuvem para escalonar, pública, privada ou ambas.
     * 0 - pública
     * 1 - privada
     * 2 - hibrida
     */
    public List<PluginInfo> filterTypeCloud(Collection<PluginInfo> plugins, int type) {
        List<PluginInfo> clouds = new ArrayList<PluginInfo>();
        //*   descobrir como identificar se uma nuvem é privada ou pública


        for (PluginInfo pluginInfo : plugins) {
            //COndição para verificar se nuvem é do tipo solicitada, privada, pública ou hibrida
//            if(pluginInfo.getTypeCloud()==type){
            clouds.add(pluginInfo);
//            }
        }
        return clouds;

    }

    /**
     * Seleciona apenas as nuvens informadas na Collection @param plgs e que tem o serviço informado no @param serviceId.
     *
     * @param plgs
     * @param serviceId
     */
    private void filterByService(long serviceId, Collection<PluginInfo> plgs) {
        ArrayList<PluginInfo> plugins = new ArrayList<PluginInfo>();

        for (PluginInfo pluginInfo : plgs) {
            if (pluginInfo.getService(serviceId) != null)
                plugins.add(pluginInfo);
        }
        plgs.retainAll(plugins);

    }

    /**
     * Implementação do algoritmo ant colony optimization.
     *
     * @param plugins
     */
    private void AlgorithmAco(List<PluginInfo> plugins){
        //Laço para define o feronômio de cada plugin(VM)
        for (PluginInfo plugin : plugins) {
            pheromone(plugin);
        }

        //lista para selecionar os plugin aleatoriamente
        List listNumberPlugin = new ArrayList<Integer>();

        //cria um número de formigas 30% menor do que o número de recursos
        int  i = 0, nAnts = (plugins.size() - (new Double(plugins.size() * 0.3).intValue()));
        while (nAnts > i) {
            Random selectPlugin = new Random();
            //gera um número aleatório para selecionar a nuvem
            listNumberPlugin.add(i,selectPlugin.nextInt(plugins.size()));
            i++;
        }
        //laço para selecionar e modificar os dados do ACO do recurso escolhido aleatoriamente
        //determina a probabilidade para decidir a próxima VM a ser visitada
        i=0;
        while(nAnts>i){
            ArrayList datas = mapAcoDatas.get(plugins.get((Integer)listNumberPlugin.get(i)).getId());
            datas.set(1, probabilityNextPlugin( ((Integer)listNumberPlugin.get(i)) , plugins));
            //errado ->  datas.set(1, multiplicationFeronomioHeuristic(plugins.get((Integer)listNumberPlugin.get(i)))/sumFeronomioHeuristic(plugins));
            if (((Double) datas.get(1)) < smallerProbability) {
                smallerProbability = (Double) datas.get(1);
            }

            i++;
        }


    }
    
    
    /**
     * Define  o feronômio do plugin passado por parâmetro. Feronômio inicial ou atualização.
     * Define os valores dos parametros de controle.
     *
     * @param plugin
     */
    private void pheromone(PluginInfo plugin){
        Double p = 0.8, pheronome=0d;

        if (mapAcoDatas.containsKey(plugin.getId())) {
            pheronome = mapAcoDatas.get(plugin.getId()).get(0);

            //colocar variação para quando achar a melhor solução
            pheronome = (1 - p) * pheronome + (1 / smallerProbability);
            mapAcoDatas.get(plugin.getId()).set(0, pheronome);
        }else{
            ArrayList datas = new ArrayList<Double>(6);
            pheronome = capacityPlugin(plugin);
            /*
            * mapeamento dos dados de cada plugin para os valores usados no ACO
            * array posição 0 para o feronômio
            * array posição 1 para a probabilidade da formiga escolher a VM
            * array posição 2 o valor da heuristica
            * array posição 3,4,5,6 valores dos reguladores do coeficeinte de controle do feronomio(alfa), da capacidade computacional(beta) e do carregamento balanceado(gama)
            * obs:  teste dos coeficientes em alfa = 2, beta = 3, gama = 3, delta = 2 
            * array posição 6 para o tempo medio de execução no plugin
            * array posição 7 para o tamanho da última tarefa executada no plugin
            * 
            * 
            */
            datas.add(0, pheronome);
            datas.add(1, 0);
            datas.add(2, 0);
            datas.add(3, 2);
            datas.add(4, 3);
            datas.add(5, 3);
            datas.add(6, 2);
            datas.add(7, 0);
            datas.add(8, 0);
            mapAcoDatas.put(plugin.getId(), datas);
        }   
    }
    /**
     * Atualiza os valores do feromônio de cada plugin de acordo com a melhor probabilidade encontrada.
     * @param listPlugin lista de plugin utilizado para realizar o escalonamento
     * @param probability melhor probabilidade encontrada
     */
    private void theBestPheromone(List<PluginInfo> listPlugin, Double probability){
        Double p = 0.01;
        
        for(PluginInfo plugin : listPlugin){
//            getDatasZookeeper(plugin, DIR_MAPACODATAS);
            Double pheronome = mapAcoDatas.get(plugin.getId()).get(0);
            pheronome = (1-p)*pheronome+(mapAcoDatas.get(plugin.getId()).get(0) /probability);
            
            mapAcoDatas.get(plugin.getId()).set(0,pheronome);
            setDatasZookeeper(plugin, DIR_MAPACODATAS, mapAcoDatas.get(plugin.getId()).toString());
        }
        
    }
    
    
    /**
     * Implementação da fórmula que define a probabilidade de escolha do próximo plugin
     *
     * @param number
     * @param plugins
     * @return a probabilidade do plugin
     */
    private Double probabilityNextPlugin(int number ,List<PluginInfo>  plugins){
        Double pb;
        PluginInfo plg = plugins.get(number);

        //laço para definir o somatorio do feronomio, capacidade computacional e carregamento balanceado
        Double sum = new Double(0);
        for(PluginInfo plugin : plugins){
            sum = sum + multiplicationDatasPlugin(plugin);
        }
        
        pb = multiplicationDatasPlugin(plg)/sum;
        plg.setRanking(pb);

        return pb;

    }

    /**
     * Multiplicação dos valores do feromônio, capacidade computacional e carregamento balanceado do plugin informado
     *
     * @param number
     * @param plugins
     * @return
     */
    private Double multiplicationDatasPlugin(PluginInfo  plugin){
        ArrayList datas = mapAcoDatas.get(plugin.getId());

        //feronomio elevado a potencia alfa(valor da variavel de controle)
        Double pheromone = getRound(Math.pow(((Double)datas.get(0)), ((Integer)datas.get(3)).doubleValue()));
        Double capacityComputing = getRound((Double)Math.pow( capacityPlugin(plugin),((Integer)datas.get(4)).doubleValue() ) );
        Double loadBalacing = getRound((Double)Math.pow( loadBalancingPlugin(plugin),((Integer)datas.get(5)).doubleValue()));
        Double capacityMemory = getRound((Double)Math.pow( getRound(plugin.getMemoryFree()),((Integer)datas.get(6)).doubleValue()));
        //((Double)Math.pow(((Float)datas.get(2)).doubleValue(), ((Float)datas.get(5)).doubleValue() )).floatValue()

//        return (new Float(formatDecimal.format(pheromone)).floatValue()* new Float(formatDecimal.format(capacityComputing)).floatValue()* new Float(formatDecimal.format(loadBalacing)).floatValue());
        return pheromone*capacityComputing*loadBalacing*capacityMemory;    
        
    }


    /**
     * Define a capacidade computacional do plugin informado
     *
     * @param plugin
     * @return
     */
    private Double capacityPlugin(PluginInfo plugin) {
        
        return (plugin.getNumCores()-plugin.getNumOccupied())* plugin.getFrequencyCore() + plugin.getLatency();

    }

    /**
     * Define o fator do balanceamento de carga do plugin informado
     *
     * @param plugin
     * @return
     */
    private Double loadBalancingPlugin(PluginInfo plugin){

        return  1 - (timeExpectedExecJob(plugin)-mapAcoDatas.get(plugin.getId()).get(7) / timeExpectedExecJob(plugin)+mapAcoDatas.get(plugin.getId()).get(7));


    }

    /**
     * Define o tempo de execução esperado para a tarefapasta 
     *
     * @param plugin
     * @param job
     * @return
     */
    private Double timeExpectedExecJob(PluginInfo plugin){
        /**
         * Descobrir tamanho das tarefas de um PLUGIN 
         *
         *
         */
        //(total do tamanho das tarefas para serem executadas)/capacidade computacional + tamanho da tarefa executada anteriormente/ latency
        return  new Double(getDatasZookeeper(plugin, DIR_SIZEALLJOBS)) /capacityPlugin(plugin) + mapAcoDatas.get(plugin.getId()).get(8)/plugin.getLatency();

    }

    /**
     * Define o tempo médio de execução da última iteração
     *
     * @param plugin
     * @param job
     * @return tempo
     */
    private Double timeAverageJob(List<PluginInfo> listPlugin){
        Double timeMax = Double.MIN_NORMAL,timeMin = Double.MAX_VALUE, time=null, timeAverage=0d;
        
        //metodo finalize job em shedservice calcula o tempo de execucao de um job,  verificar como armazenar esse tempo em algum lugar
        
        int cont=0;
        for(PluginInfo plugin : listPlugin){

            time = new Double(getDatasZookeeper(plugin, DIR_SIZEALLJOBS))/((plugin.getNumCores()-plugin.getNumOccupied())*plugin.getFrequencyCore());
            
            if(time<timeMin){
                timeMin = time;
            }else if(time>timeMax){
                timeMax = time;
            }
            timeAverage = timeAverage+time;
            cont++;
        }
        
        timeAverage = timeAverage/cont;

        
        
        return (timeMax-timeMin)/timeAverage;

    }
    
    
    private Double getRound(Double value){
        
        return Math.rint(value*1000)/1000;
    }

    /**
     * Retorna os valores armazenados no zookeeper contendo os dados utilizados pelo ACO de cada plugin.
     * @param listClouds a lista de plugins que podem conter dados
     * @return o mapa com os dados ACO de cada plugin
     */
    private HashMap getMapAcoDatasZooKeeper(List<PluginInfo> listClouds){
        HashMap map = new HashMap<String, ArrayList<Double>>();
        String datasString ;
        Iterator it=  listClouds.iterator();
        while(it.hasNext()){
            PluginInfo plugin= (PluginInfo)it.next();
            datasString = getDatasZookeeper(plugin, DIR_MAPACODATAS);
            map.put(plugin.getId(),getListDouble(datasString));
        }
    
        return map;
    }
    
    /**
     * Grava os valores utilizados pelo ACO de cada plugin no zookeeper.
     */
    private void setMapAcoDatasZooKeeper(List<PluginInfo> listClouds){

        Iterator it=  listClouds.iterator();
        while(it.hasNext()){
            PluginInfo plugin= (PluginInfo)it.next();
            setDatasZookeeper(plugin, DIR_MAPACODATAS, mapAcoDatas.get(plugin.getId()).toString());
        }
    
    }
    
    /**
     * Recupera os dados do @param plugin, pluginInfo, armazenados no zookeeper de acordo com o @param dir, diretorio, informado.
     * @param plugin identificação do recurso que deve ser retirado os dados.
     * @param dir diretório do zookeeper que contém as informações desejadas.
     * @return dados contidos no diretorio
     */
    private String getDatasZookeeper(PluginInfo plugin, String dir){
        String datas = null;
        
        try {
            datas = zk.getData(plugin.getPath_zk()+dir, new UpdatePeerData());
        } catch (KeeperException ex) {
            Logger.getLogger(AcoSched.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(AcoSched.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        
        return datas;
    }
     /**
     * Recupera os dados do @param plugin, pluginInfo, armazenados no zookeeper de acordo com o @param dir, diretorio, informado.
     * @param plugin identificação do recurso que deve ser retirado os dados.
     * @param dir diretório do zookeeper que contém as informações desejadas.
     * @return dados contidos no diretorio
     */
    private void setDatasZookeeper(PluginInfo plugin, String dir, String datas){
        try {
            zk.setData(plugin.getPath_zk()+dir, datas);
        } catch (KeeperException ex) {
            Logger.getLogger(AcoSched.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(AcoSched.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        //como deve ser tratado caso o diretorio nao exista???
        
    }
    /**
     * Verifica se o pluginInfo está disponível.
     * @param plugin identificação do plugin solicitado
     * @return true se o plugin estiver disponível
     */
    private Boolean getStatusCloudZookeeper(PluginInfo plugin, boolean watch){
        try {
            
            return zk.getZNodeExist(plugin.getPath_zk()+"/STATUS",watch);
            
        } catch (KeeperException ex) {
            Logger.getLogger(AcoSched.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(AcoSched.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return false;
    }
    
     /**
     * Retorna uma array de object de acordo com os datas passados por parâmetro, realizando a divição da string de acordo com as
     * vígurlas.
     * @param datas
     * @return 
     */
    private ArrayList<Double> getListDouble(String datas){
        ArrayList list = new ArrayList<Double>();
        int i=0,cont=0;
        
        while(datas.length()>0){
            Double value = new Double(datas.substring(i, (datas.indexOf(",")==-1) ? datas.length() : datas.indexOf(",")));
            list.add(cont, value);
            datas = datas.substring((datas.length()==1) ? datas.length() : datas.indexOf(",")+1, datas.length());
            cont++;
        }
        
        return list;
    }

    @Override
    public HashMap<JobInfo, PluginInfo> schedule(Collection<JobInfo> jobInfos) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    

    public static class UpdatePeerData implements Watcher {

        @Override
        public void process(WatchedEvent event) {
            System.out.println(event);

        }
    }
   
    /**
     * Utilização do algoritmo de ordenação quicksort para ordenar as melhores nuvens para escalonar de acordo com o parâmentro @value passado para os tipos
     * Integer e Long
     *
     * @param value   valor que deve ser utilizado para ordenação da melhor nuvem
     * @param start   primeira nuvem da lista a considerar
     * @param end     ultima nuvem da lista a considerar
     * @param plugins nuvens para ordenadar
     */
    private void quickSort(int start, int end, List<PluginInfo> plugins) {
        PluginInfo pluginTemp;
        int i = start, f = end;                   // Extremos  
        int x = start + (end - start) / 2; // Pivô aleatório
        Double pivo = plugins.get(x).getRanking();                    // evita quadrático 

        while (i <= f) {                    // Não cruzaram 

            while (i < start && (plugins.get(i).getRanking() > pivo)) {// Organiza 1ª metade
                i++;
            }

            while (f > end && !(plugins.get(i).getRanking() > pivo)) {// Organiza 2ª metade
                f--;
            }

            if (i <= f) {                     // Se ainda não acabou 
                pluginTemp = plugins.get(f);                     // troca os elementos 
                plugins.set(f--, plugins.get(i));                // dos dois lados 
                plugins.set(i++, pluginTemp);                   // da lista
            }

        }
        if (start < f) {
            quickSort(start, f, plugins);
        }           // Ordena 1ª metade 
        if (i < end) {
            quickSort(i, end, plugins);
        }           // Ordena 2ª metade 


    }


}
//2181 porta zookeper