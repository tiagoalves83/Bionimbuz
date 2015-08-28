package br.unb.cic.bionimbus.toSort;

import br.unb.cic.bionimbus.avro.gen.Pair;
import br.unb.cic.bionimbus.avro.rpc.AvroClient;
import br.unb.cic.bionimbus.avro.rpc.RpcClient;
import br.unb.cic.bionimbus.client.JobInfo;
import br.unb.cic.bionimbus.client.PipelineInfo;
import br.unb.cic.bionimbus.client.experiments.MscTool;
import br.unb.cic.bionimbus.config.BioNimbusConfig;
import br.unb.cic.bionimbus.plugin.PluginInfo;
import br.unb.cic.bionimbus.plugin.PluginService;
import br.unb.cic.bionimbus.services.messaging.CloudMessageService;
import br.unb.cic.bionimbus.services.messaging.CuratorMessageService;
import br.unb.cic.bionimbus.services.messaging.CuratorMessageService.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author will
 */
public class SchedullerTester {

    private static final Logger LOG = LoggerFactory.getLogger(MscTool.class);
//    private static StringBuilder result = new  StringBuilder();

    private RpcClient rpcClient;
    private BioNimbusConfig config;
    private CloudMessageService cms;
    private RepositoryService rs;

    public SchedullerTester() {
        initCommunication();
    }

    private void initCommunication() {
        cms = new CuratorMessageService();
        cms.connect("192.168.1.12:2181");
        rs = new RepositoryService(cms);
        
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try {
            config = mapper.readValue(new File("conf/node.yaml"), BioNimbusConfig.class);
            rpcClient = new AvroClient(config.getRpcProtocol(), config.getHost().getAddress(), config.getRpcPort());
            if (rpcClient.getProxy().ping()) {
                LOG.info("client is connected.");
            }

        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(MscTool.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(MscTool.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void main(String[] args) throws InterruptedException, IOException {
        SchedullerTester tester = new SchedullerTester();
        boolean fileTest = false;
        
        if (fileTest) {
            try {
                tester.runJobs();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            List<PipelineInfo> pipelines = PipelineTestGenerator.getPipelinesTemplates();
            List<PluginService> services = PipelineTestGenerator.getServicesTemplates();
            List<PluginInfo> resources = PipelineTestGenerator.getResourceTemplates();
            
            // flush test data
            System.out.println("[SchedTester] flushing test data");
            PrintWriter pwr = new PrintWriter("pipelines.txt", "UTF-8");
            for (PipelineInfo p : pipelines)
                pwr.println(p.toString());
            PrintWriter swr = new PrintWriter("services.txt", "UTF-8");
            for (PluginService s : services)
                swr.println(s.toString());
            PrintWriter rwr = new PrintWriter("resources.txt", "UTF-8");
            for (PluginInfo r : resources)
                rwr.println(r.toString());
            
            // add data to zookeeper
            tester.addServices(services);
            tester.addResources(resources);
            
            System.out.println("[SchedTester] starting testing with " + pipelines.size() + " pipelines");
            
            // perform all tests
            PipelineInfo fst = pipelines.get(0);
            tester.sendJobs(fst);
            pipelines.remove(fst);
            System.out.println("[SchedTester] First pipeline " + fst.getId() + " sent, " + pipelines.size() + " remaining");
            
            // busy waiting to wait for node to exists
            while(!tester.cms.getZNodeExist(Path.PREFIX_PIPELINE.getFullPath(fst.getId()), null)){
            System.out.println("[SchedTester] waiting node creation");}
            tester.cms.getChildren(Path.PREFIX_PIPELINE.getFullPath(fst.getId()), new SendPipeline(tester.cms, tester, pipelines, fst.getId()));
            
            System.out.println("[SchedTester] waiting forever");
            while(true){}
        }
    }
    
    public void addServices (List<PluginService> services) {
        for (PluginService service : services)
            rs.addServiceToZookeeper(service);
    }
    
    public void addResources (List<PluginInfo> resources) {
        for (PluginInfo resource : resources)
            rs.addPeerToZookeeper(resource);
    }

    public void runJobs() throws IOException, InterruptedException {

        // get pipeline from file
        PipelineInfo pipeline = getPipelineFromFile();
        
        for(JobInfo j : pipeline.getJobs()) {
            System.out.println(j.toString());
        }

        // send pipeline for execution
        sendJobs(pipeline);

        // get results when ready
//        List<String> peers = cms.getChildren(cms.getPath().PREFIX_PIPELINE.getFullPath("", "", "", pipeline.getId()), null);
//        for (String path : peers) {
//            cms.getChildren(path + Path.SCHED, new ShowSchedResults());
//        }
    }
    
    private PipelineInfo getPipelineFromFile() throws FileNotFoundException, IOException {
        
        JobInfo taskList[];
        
        // get pipeline file path
        String pathHome = System.getProperty("user.dir");
        String path =  (pathHome.substring(pathHome.length()).equals("/") ? pathHome+"data-folder/" : pathHome+"/data-folder/");
        BufferedReader br = new BufferedReader(new FileReader(path+"pipelineSample.txt"));
        
        // get first line: number of tasks
        String line = br.readLine();
        int tasksNumber = Integer.parseInt(line);
        taskList = new JobInfo[tasksNumber];
        
        // get next tasksNumber lines: each task
        for (int i=0; i<tasksNumber; i++) {
            // generate a new jobInfo from json
            line = br.readLine();
            JobInfo jobInfo = new JobInfo();
            jobInfo.setTimestamp(0l);
            
            // set serviceId from json
            int lastComa = line.indexOf(",");
            jobInfo.setServiceId(line.substring(line.indexOf("serviceId:")+10, lastComa));
            
            // set args from json
            lastComa = line.indexOf(",", lastComa+1);
            jobInfo.setArgs(line.substring(line.indexOf("args:")+5, lastComa));
            
            // get input list from json
            int lastBracket = line.indexOf("]");
            String io = line.substring(line.indexOf("inputs:[")+8, lastBracket);
            String inputs[] = io.split(",");
            
            // set inputs
            // TODO: change addInput to receive the filename instead of its zookeeper id
            for (String inp : inputs)
                jobInfo.addInput(inp, 0l);
            
            // get output list from json
            lastBracket = line.indexOf("]", lastBracket+1);
            io = line.substring(line.indexOf("outputs:[")+9, lastBracket);
            String outputs[] = io.split(",");
            
            // set outputs
            for (String out : outputs)
                jobInfo.addOutput(out);
            
            // put it into the map to, furthermore, set the dependencies
            taskList[i] = jobInfo;
        }
        
        // get the remaining lines: dependency matrix
        for (int i=0; i<tasksNumber; i++) {
            String deps[] = br.readLine().split(",");
            for (int j=0; j<tasksNumber; j++) {
                if (Integer.parseInt(deps[j]) == 1) {
                    taskList[i].addDependency(taskList[j].getId());
                }
            }
        }
        
        // add all jobs to a pipeline
        PipelineInfo pipeline = new PipelineInfo();
        for(JobInfo task : taskList) {
            pipeline.addJob(task);
        }
        
        // get task list with dependencies        
        return pipeline;
    }
    
    public void sendJobs(PipelineInfo pipeline) throws InterruptedException, IOException {
//        communication.sendReq(new JobReqMessage(p2p.getPeerNode(), jobs), P2PMessageType.JOBRESP);
//        JobRespMessage resp = (JobRespMessage) communication.getResp();
        List<br.unb.cic.bionimbus.avro.gen.JobInfo> listjob = new ArrayList<br.unb.cic.bionimbus.avro.gen.JobInfo>();
        for (JobInfo jobInfo : pipeline.getJobs()) {
            br.unb.cic.bionimbus.avro.gen.JobInfo job = new br.unb.cic.bionimbus.avro.gen.JobInfo();
            job.setArgs(jobInfo.getArgs());
            job.setId(jobInfo.getId());
            job.setLocalId(config.getHost().getAddress());
            job.setServiceId(jobInfo.getServiceId());
            job.setTimestamp(jobInfo.getTimestamp());
            List<Pair> listPair = new ArrayList<Pair>();
            for (br.unb.cic.bionimbus.utils.Pair<String, Long> pairInfo : jobInfo.getInputs()) {
                Pair pair = new Pair();
                pair.first = pairInfo.first;
                pair.second = pairInfo.second;
                listPair.add(pair);
            }
            job.setInputs(listPair);
            job.setOutputs(jobInfo.getOutputs());

            listjob.add(job);
        }
        
        br.unb.cic.bionimbus.avro.gen.PipelineInfo avroPipeline = new br.unb.cic.bionimbus.avro.gen.PipelineInfo();
        avroPipeline.setId(pipeline.getId());
        avroPipeline.setJobs(listjob);

        rpcClient.getProxy().startPipeline(avroPipeline);
    }

    public static class ShowSchedResults implements Watcher {

        @Override
        public void process(WatchedEvent event) {
            System.out.println(event);
        }
    }
    
    public static class SendPipeline implements Watcher  {

        private final CloudMessageService cms;
        private final SchedullerTester st;
        private final List<PipelineInfo> remaining;
        private final String prevId;

        public SendPipeline(CloudMessageService cms, SchedullerTester st, List<PipelineInfo> remaining, String prevId) {
            this.cms = cms;
            this.st = st;
            this.remaining = remaining;
            this.prevId = prevId;
        }

        /**
         * Recebe as notificações de evento do zookeeper.
         * @param event evento que identifica a mudança realizada no zookeeper
         */
        @Override
        public void process(WatchedEvent event){
            System.out.println("[SentPipeline] Event got: " + event.toString());
            switch(event.getType()){
                case NodeDeleted:
                    try {
                        if (!remaining.isEmpty()) {
                            PipelineInfo pipeline = remaining.get(0);

                            // send new pipeline
                            st.sendJobs(pipeline);

                            // set new watcher with remaining pipelines
                            remaining.remove(pipeline);
                            System.out.println("[SentPipeline] Pipeline " + pipeline.getId() + " sent, " + remaining.size() + " remaining");
                            while(!cms.getZNodeExist(Path.PREFIX_PIPELINE.getFullPath(pipeline.getId()), null)){}
                            cms.getChildren(Path.PREFIX_PIPELINE.getFullPath(pipeline.getId()), new SendPipeline(cms, st, remaining, pipeline.getId()));
                        } else {
                            System.out.println("[SentPipeline] No more pipelines");
                        }
                    } catch (InterruptedException ex) {
                        java.util.logging.Logger.getLogger(SchedullerTester.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(SchedullerTester.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    break;
                default:
                    System.out.println("[SendPipeline] Received other event: " + event.getPath());

            }
        } 
    }
}