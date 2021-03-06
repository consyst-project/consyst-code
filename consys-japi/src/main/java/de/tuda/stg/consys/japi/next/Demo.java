package de.tuda.stg.consys.japi.next;

import de.tuda.stg.consys.core.store.cassandra.CassandraConsistencyLevels;
import de.tuda.stg.consys.core.store.utils.Address;
import de.tuda.stg.consys.japi.next.binding.Akka;
import de.tuda.stg.consys.japi.next.binding.Cassandra;
import scala.Option;
import scala.Serializable;
import scala.collection.immutable.Seq;
import scala.concurrent.duration.Duration;

import java.util.Arrays;

/**
 * Created on 27.01.20.
 *
 * @author Mirko Köhler
 */
public class Demo {

	public static class Box implements Serializable {
		private int i = 0;
		public void inc() {
			i++;
		}
		public int get() {
			return i;
		}
	}




	public static void main(String[] args) throws Exception {
		Cassandra.ReplicaBinding replica1 = Cassandra.newReplica(
			"127.0.0.1", 9042, 2181, Duration.apply(60, "s"), true
		);

		Cassandra.ReplicaBinding replica2 = Cassandra.newReplica(
			"127.0.0.2", 9042, 2182, Duration.apply(60, "s"), false
		);

//		Akka.ReplicaBinding replica1 = Akka.newReplica(
//				"127.0.0.1", 4121, 2181,
//				Arrays.asList(
//						Address.apply("127.0.0.1", 4121),
//						Address.apply("127.0.0.2", 4122)
//				),
//				Duration.apply(60, "s")
//		);
//
//		Akka.ReplicaBinding replica2 = Akka.newReplica(
//				"127.0.0.2", 4122, 2182,
//				Arrays.asList(
//						Address.apply("127.0.0.1", 4121),
//						Address.apply("127.0.0.2", 4122)
//				),
//				Duration.apply(60, "s")
//		);

		System.out.println("transaction 1");
		replica1.transaction(ctx -> {
			Ref<Box> box1 = ctx.replicate("box1",CassandraConsistencyLevels.STRONG(), Box.class);
			box1.ref().inc();
			return Option.apply(2);
		});

		System.out.println("transaction 2");
		replica1.transaction(ctx -> {
			Ref<Box> box1 = ctx.lookup("box1", Box.class, CassandraConsistencyLevels.STRONG());
			box1.ref().inc();
			int i = box1.ref().get();
			System.out.println(i);
			return Option.empty();
		});

		replica1.close();
		replica2.close();
	}

}
