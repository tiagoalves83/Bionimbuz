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

package br.unb.cic.bionimbus.plugin;

import br.unb.cic.bionimbus.model.FileInfo;
import br.unb.cic.bionimbus.services.messaging.CloudMessageService;
import br.unb.cic.bionimbus.services.messaging.CuratorMessageService.Path;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginTaskRunner implements Callable<PluginTask> {

    private final PluginTask task;
    private final PluginService service;
    private final String path;
    private final CloudMessageService cms;
    private final String PATHFILES="data-folder/";
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginTaskRunner.class.getSimpleName());


    public PluginTaskRunner(AbstractPlugin plugin, PluginTask task,
                            PluginService service, String path,CloudMessageService cms) {
        this.service = service;
        this.task = task;
        this.path = path;
        this.cms = cms;
    }

    
    @Override
    public PluginTask call() throws Exception {
        
        String args = task.getJobInfo().getArgs();
        List<FileInfo> inputFiles = task.getJobInfo().getInputFiles();        
        int i = 1;
        for (FileInfo info : inputFiles) {
            String input = info.getName();
            //linha comentada pois arquivos de entrada não ficam mais no AbstractPlugin
            //args = args.replaceFirst("%I" + i, path + File.pathSeparator + plugin.getInputFiles().get(input).first);
            args = args.replaceFirst("%I" + i, path+PATHFILES + input+" ");
            i++;
        }

        List<String> outputs = task.getJobInfo().getOutputs();
        i = 1;
        for (String output : outputs) {
            args = args.replaceFirst("%O" + i, " "+path+PATHFILES + output);
            i++;
        }
        Process p;
        try {
            System.out.println("[PluginTaskRunner] exec: " + service.getPath() + " " + args);
            p = Runtime.getRuntime().exec(service.getPath() + " " + args);
//                        p = Runtime.getRuntime().exec(path+service.getPath().substring(1,service.getPath().length()) + " " + args);

            task.setState(PluginTaskState.RUNNING);
           
            if(cms!=null)
                cms.setData(Path.NODE_TASK.getFullPath(task.getPluginExec(), task.getJobInfo().getId()), task.toString());
            
            BufferedReader saidaSucesso = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader saidaErro = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            int j=0;
            while ((line = saidaSucesso.readLine()) != null && j<6) {
                System.out.println("Job "+task.getJobInfo().getId()+". Saída: "+line);
                j++;
                
            }
            while ((line = saidaErro.readLine()) != null) {
                LOGGER.error("ERRO job ID:" + task.getJobInfo().getId()+"-  Arquivo de saída: "+task.getJobInfo().getOutputs()+", Resposta :"+line);

            }
            
            if(p.waitFor()==0){
                long time =System.currentTimeMillis();
                task.setTimeExec(((float) (time - task.getJobInfo().getTimestamp()) / 1000));
                LOGGER.info("Tempo final do job de saída: "+task.getJobInfo().getOutputs()+" - MileSegundos: " + time);

                task.setState(PluginTaskState.DONE);
            }else {
                task.setTimeExec(((float) (System.currentTimeMillis() - task.getJobInfo().getTimestamp()) / 1000));
                task.setState(PluginTaskState.ERRO);
            }   

            if(cms!=null)
                cms.setData(Path.NODE_TASK.getFullPath(task.getPluginExec(), task.getJobInfo().getId()), task.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }

        return task;
    }
}
