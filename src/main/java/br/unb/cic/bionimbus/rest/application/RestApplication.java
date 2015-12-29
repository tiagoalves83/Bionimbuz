/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.unb.cic.bionimbus.rest.application;

import br.unb.cic.bionimbus.rest.resource.FileResource;
import br.unb.cic.bionimbus.rest.resource.PingResource;
import br.unb.cic.bionimbus.rest.resource.PipelineResource;
import br.unb.cic.bionimbus.rest.resource.UserResource;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.eclipse.jetty.util.resource.Resource;

/**
 *
 * @author Vinicius
 */
public class RestApplication extends Application {

    @SuppressWarnings("rawtypes")
    private static final Set SERVICES = new HashSet();

    /**
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! !!! IMPORTANT !!!
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Everytime a class that
     * defines a resource is created, it's ESSENCIAL that is added to Services
     * Set
     */
    @SuppressWarnings("unchecked")
    public RestApplication() {
        SERVICES.add(new UserResource());
        SERVICES.add(new FileResource());
        SERVICES.add(new PingResource());
        SERVICES.add(new PipelineResource());
    }

    private static final Set<Class<?>> CLASSES;

    static {
        HashSet<Class<?>> tmp = new HashSet<>();
        tmp.add(Resource.class);

        CLASSES = Collections.unmodifiableSet(tmp);
    }

    @Override
    public Set<Class<?>> getClasses() {

        return CLASSES;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Set getSingletons() {
        return SERVICES;
    }

    @SuppressWarnings("rawtypes")
    public static Set getServices() {
        return SERVICES;
    }
}
