package com.safjnest.Utilities.Controller.Interface;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class ApiClass {
    @Id
    private int ID;
    
    private String name;

    public ApiClass(int ID, String name) {
        this.ID = ID;
        this.name = name;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


}
