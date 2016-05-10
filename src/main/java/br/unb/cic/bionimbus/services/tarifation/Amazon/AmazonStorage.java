/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.unb.cic.bionimbus.services.tarifation.Amazon;

import br.unb.cic.bionimbus.services.tarifation.Instance;

/**
 *
 * @author Gabriel Fritz Sluzala
 */
public class AmazonStorage implements Instance{

    private boolean activationStatus;
    private int id;
    private String region;
    private String kind;
    private double price;
    private String PriceUnit;
    private String createdAt;
    private String updatedAt;

    /**
     *
     * @param id
     * @param region
     * @param kind
     * @param price
     * @param PriceUnit
     * @param createdAt
     * @param updatedAt
     */
    public AmazonStorage(int id, String region, String kind, double price, String PriceUnit, String createdAt, String updatedAt) {
        this.id = id;
        this.region = region;
        this.kind = kind;
        this.price = price;
        this.PriceUnit = PriceUnit;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     *
     * @return
     */
    @Override
    public String status() {
        return activationStatus+"";
    }

    /**
     *
     * @return
     */
    @Override
    public String getId() {
        return id+"";
    }

    /**
     *
     * @return
     */
    public String getRegion() {
        return region;
    }

    /**
     *
     * @return
     */
    public String getKind() {
        return kind;
    }

    /**
     *
     * @return
     */
    @Override
    public String getPrice() {
        return price+"/"+this.getPriceUnit().toUpperCase();
    }

    /**
     *
     * @return
     */
    public String getPriceUnit() {
        return PriceUnit;
    }

    /**
     *
     * @return
     */
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     *
     * @return
     */
    public String getUpdatedAt() {
        return updatedAt;
    }

    /**
     *
     * @return
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + this.id;
        return hash;
    }

    /**
     *
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AmazonStorage other = (AmazonStorage) obj;
        return this.id == other.id;
    }

    @Override
    public String getName() {
        return this.getKind();
    }
}