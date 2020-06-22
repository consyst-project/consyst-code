package de.tuda.stg.consys.demo.counter;

import com.typesafe.config.Config;
import de.tuda.stg.consys.core.Ref;
import de.tuda.stg.consys.core.ReplicatedObject;
import de.tuda.stg.consys.core.akka.AkkaReplicaSystemFactory;
import de.tuda.stg.consys.core.akka.AkkaReplicaSystems;
import de.tuda.stg.consys.core.akka.DeltaCRDTAkkaReplicaSystem;
import de.tuda.stg.consys.demo.DemoBenchmark;
import de.tuda.stg.consys.demo.counter.schema.AddOnlySet;
import de.tuda.stg.consys.japi.JConsistencyLevels;
import de.tuda.stg.consys.japi.JRef;
import de.tuda.stg.consys.japi.impl.akka.JAkkaRef;
import org.checkerframework.com.google.common.collect.Sets;

/**
 * Created on 10.10.19.
 *
 * @author Mirko Köhler
 */
public class CounterBenchmark extends DemoBenchmark {
	public static void main(String[] args) {
		start(CounterBenchmark.class, args[0]);
	}

	public CounterBenchmark(Config config) {
		super(config);
	}

	private JRef<AddOnlySetString> set;

	@Override
	public void setup() {
		System.out.println("setup");
		if (processId() == 0) {
			set = system().replicate("counter", new AddOnlySetString(), JConsistencyLevels.DCRDT);
		} else {
			set = system().<AddOnlySetString>lookup("counter", AddOnlySetString.getClass(), JConsistencyLevels.DCRDT);
			set.sync(); //Force dereference
		}
		System.out.println(processId() + " finished setup");
	}

	@Override
	public void operation() {
		// we need a way to access the object without ref().
		// this is an extremely ugly way to access it
		// the framework needs to be adapted for easier access

		JAkkaRef<AddOnlySetString> c = (JAkkaRef<AddOnlySetString>) set;
		Ref<String, AddOnlySetString> setRef = c.getRef();
		ReplicatedObject<String, AddOnlySetString> deref = setRef.deref();
		DeltaCRDTAkkaReplicaSystem.DeltaCRDTReplicatedObject o = (DeltaCRDTAkkaReplicaSystem.DeltaCRDTReplicatedObject) deref;
		AddOnlySetString derefderef = (AddOnlySetString) o.t();
		derefderef.addElement("Hello from " + processId());
		doSync(() -> set.sync());
		System.out.print(".");
	}

	@Override
	public void cleanup() {
		system().clear(Sets.newHashSet());
	}


}
