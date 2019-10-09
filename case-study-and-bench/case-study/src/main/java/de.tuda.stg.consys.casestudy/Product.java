package de.tuda.stg.consys.casestudy;

import de.tuda.stg.consys.checker.qual.Weak;
import de.tuda.stg.consys.collections.JRefDistList;
import de.tuda.stg.consys.objects.actors.AkkaReplicaSystem;
import de.tuda.stg.consys.objects.japi.JConsistencyLevel;
import de.tuda.stg.consys.objects.japi.JRef;
import de.tuda.stg.consys.objects.japi.JReplicaSystem;
import de.tuda.stg.consys.objects.japi.JReplicated;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Optional;

public class Product implements Serializable, JReplicated {

    /* This field is needed for JReplicated */
    public transient AkkaReplicaSystem<String> replicaSystem = null;

    private double cost;

    private String name;

    public JRef<@Weak JRefDistList> comments;

    Product(String ProductName, double cost) {
        this.name = ProductName;
        this.cost = cost;
    }

    public boolean init(){
        Optional<JReplicaSystem> systemOptional = getSystem();
        JReplicaSystem system;
        if(systemOptional.isPresent())
            system = systemOptional.get();
        else
            return false;

        comments = system.replicate(new JRefDistList(JConsistencyLevel.WEAK), JConsistencyLevel.WEAK);
        return true;
    }

    public double getCost(){
        return cost;
    }

    public String getName(){
        return name;
    }

    public String getComments(){
        LinkedList<JRef<@Weak Comment>> localComments =
                comments.invoke("getAsNonReplicatedLinkedList", true);
        String ret = "";
        for (int x = 0; x < localComments.size(); x++){
            JRef<@Weak Comment> com = localComments.get(x);
            ret += "----------------------------------------------" + System.getProperty("line.separator");
            ret += com.invoke("getAuthor") + " says: " + System.getProperty("line.separator");
            ret += com.invoke("getContent") + System.getProperty("line.separator");
        }
        return ret;
    }
}
