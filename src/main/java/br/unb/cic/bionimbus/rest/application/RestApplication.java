/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.unb.cic.bionimbus.rest.application;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;

/**
 *
 * @author Vinicius
 */
public class RestApplication extends Application {

    @SuppressWarnings("rawtypes")
    private static Set services = new HashSet();

    /**
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * !!!                IMPORTANT               !!! 
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * Everytime a class that defines a resource is 
     * created, it's ESSENCIAL that is added to Services Set
     */
    @SuppressWarnings("unchecked")
    public RestApplication() {

    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Set getSingletons() {
        return services;
    }

    @SuppressWarnings("rawtypes")
    public static Set getServices() {
        return services;
    }
}
