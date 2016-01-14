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
package br.unb.cic.bionimbus.utils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

/**
 * Classe com os metodos para a realização de um upload na federação
 *
 * @author Deric
 */
public class Put {

    private JSch jsch = new JSch();
    private Session session = null;
    private String address;
    private String USER = "zoonimbus";
    private String PASSW = "Zoonimbus1";
    private int PORT = 22;
    private Channel channel;
    private String path;

    public Put(String address, String path) {
        this.address = address;
        this.path = path;
    }

    public Put() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Método que realiza a conexão entre o cliente e o servidor ou entre
     * servidores, para upar um arquivo em um peer.
     *
     * @return - true se o upload foi realizado com sucesso, false caso
     * contrário
     * @throws JSchException
     * @throws SftpException
     */
    public boolean startSession() throws JSchException, SftpException {
        String pathDest = "/home/zoonimbus/zoonimbusProject/data-folder/";
        try {

            session = jsch.getSession(USER, address, PORT);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(PASSW);
            session.connect();
        } catch (JSchException e) {
            e.getCause();
            e.printStackTrace();
            
            return false;
        }

        try {
            this.channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp sftpChannel = (ChannelSftp) channel;
            /*
             * Sem setar nenhuma permissao o arquivo chega trancado no destino, sendo acessado apenas pelo root,
             * portanto preferi dar um 777 antes de enviar o arquivo para que chegue livre ao destino.
             * Por questões de segurança, talvez isso deva ser repensado futuramente.
             */
            //sftpChannel.chmod(777, path);
            System.out.println("\n Uploading file.....\n\n\n");
            sftpChannel.put(path, pathDest);
            sftpChannel.exit();
            session.disconnect();

        } catch (JSchException a) {
            return false;
        }
        return true;

    }
}
