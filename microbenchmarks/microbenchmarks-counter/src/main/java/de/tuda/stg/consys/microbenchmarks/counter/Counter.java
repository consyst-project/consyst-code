package de.tuda.stg.consys.microbenchmarks.counter;

import java.io.Serializable;

public class Counter implements Serializable {
    public int value;
    public void inc() {
        value++;
    }

    public Counter(int value) {
        this.value = value;
    }
}