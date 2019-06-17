package de.tuda.stg.consys.casestudy;

import de.tuda.stg.consys.objects.japi.JReplicaSystem;

import java.io.Serializable;

public class Main implements Serializable {

    public static void main(String... args) throws Exception {

    }

    JReplicaSystem[] replicaSystems;

    private void setUpReplicaSystems(int systemCount){
        replicaSystems = new JReplicaSystem[systemCount];

        for (int i = 0; i < systemCount; i++) {
            replicaSystems[i] = JReplicaSystem.fromActorSystem(2552 + i);
        }

        for (int i = 0; i < systemCount; i++) {
            for (int j = 0; j < systemCount; j++) {
                if (i != j)
                    replicaSystems[i].addReplicaSystem("127.0.0.1", 2552 + j);
            }
        }
    }


}
