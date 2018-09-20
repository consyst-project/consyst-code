package de.tudarmstadt.consistency.store

import de.tudarmstadt.consistency.store.examples.BankingStore
import org.junit.Assert._
import org.junit.Test

import scala.concurrent.ExecutionContext

/**
	* Created on 05.09.18.
	*
	* @author Mirko Köhler
	*/
class SimpleMultiStoreTest extends SimpleStoreTest.Multi[Integer] with BankingStore {



	@Test
	def testMultipleSessions(): Unit = {
		implicit val executionContext : ExecutionContext = ExecutionContext.global

		//Sequential
		val store = stores(0)

		store.startSession { session =>
			import store._
			//Commit a transaction
			session.startTransaction(IsolationLevels.SI) { tx =>
				deposit(tx, ConsistencyLevels.CAUSAL)("alice", 1000)
				deposit(tx, ConsistencyLevels.CAUSAL)("bob", 1000)
				deposit(tx, ConsistencyLevels.CAUSAL)("carol", 1000)
				Some()
			}
		}
		//Make sure that the data has been propagated to all replicas
		Thread.sleep(500)

		//Parallel
		val store1 = stores(1)
		val future1 = parallelSession(store1) { session =>
			import store1._

			//Commit a transaction
			val tx1 = session.startTransaction(IsolationLevels.SI) { tx =>
				transfer(tx, ConsistencyLevels.CAUSAL)("alice", "bob", 200)
				Some()
			}
			println(s"future 1, tx1 $tx1")
		}

		val store2 = stores(2)
		val future2 = parallelSession(store2) { session =>
			import store2._

			//Commit a transaction
			val tx1 = session.startTransaction(IsolationLevels.SI) { tx =>
				transfer(tx, ConsistencyLevels.CAUSAL)("alice", "carol", 300)
				Some()
			}
			println(s"future 2, tx1 $tx1")
		}

		val store3 = stores(3)
		val future3 = parallelSession(store3) { session =>
			import store3._

			//Commit a transaction
			val tx1 = session.startTransaction(IsolationLevels.SI) { tx =>
				transfer(tx, ConsistencyLevels.CAUSAL)("alice", "carol", 50)
				Some()
			}
			println(s"future 3, tx1 $tx1")
		}

		barrier(future1, future2, future3)


		//Sequential
		store.startSession { session =>
			import store._

			session.startTransaction(IsolationLevels.SI) { tx =>
				val a = tx.read("alice", ConsistencyLevels.CAUSAL)
				val b = tx.read("bob", ConsistencyLevels.CAUSAL)
				val c = tx.read("carol", ConsistencyLevels.CAUSAL)

				(a, b, c) match {
					case (Some(aVal), Some(bVal), Some(cVal)) =>
						assertEquals("after money transfer the sum can not change", 3000, aVal + bVal + cVal)
					case t =>
						fail(s"not all reads could be resolved: $t")
				}

				Some()
			}
		}
	}

	@Test
	def testMultipleSessionRepeatedly(): Unit = {
		for (i <- 0 to 15) {
			resetStores()
			testMultipleSessions()
		}
	}

	@Test
	def testReadWriteConflict(): Unit = {
		implicit val executionContext : ExecutionContext = ExecutionContext.global

		//Sequential
		val store = stores(0)
		store.startSession { session =>
			import store._
			//Commit a transaction
			session.startTransaction(IsolationLevels.SI) { tx =>
				tx.write("alice", 1000, ConsistencyLevels.CAUSAL)
				tx.write("bob", 0, ConsistencyLevels.CAUSAL)
				tx.write("carol", 0, ConsistencyLevels.CAUSAL)
				Some()
			}
		}

		//Make sure that the data has propagated to all replicas
		Thread.sleep(500)

		//Parallel
		val store1 = stores(1)
		val future1 = parallelSession(store1) { session =>
			import store1._

			//Commit a transaction
			val tx1 = session.startTransaction(IsolationLevels.SI) { tx =>
				tx.read("alice", ConsistencyLevels.CAUSAL) match {
					case Some(a) =>
						Thread.sleep(1000) //<- Wait to let the other transaction finish between reading and writing
						tx.write("alice", a - 200, ConsistencyLevels.CAUSAL)
						tx.write("bob", 200, ConsistencyLevels.CAUSAL)
					case _ =>
				}
				Some ()
			}
			println(s"future 1, tx1 $tx1")
		}

		val store2 = stores(2)
		val future2 = parallelSession(store2) { session =>
			import store2._

			Thread.sleep(300) //<- Make sure that the read from the other tx has been processed

			//Commit a transaction
			val tx1 = session.startTransaction(IsolationLevels.SI) { tx =>
				transfer(tx, ConsistencyLevels.CAUSAL)("alice", "carol", 300)
				Some ()
			}
			println(s"future 2, tx1 $tx1")
		}

		barrier(future1, future2)

		//Sequential
		store.startSession { session =>
			import store._
			session.startTransaction(IsolationLevels.SI) { tx =>
				val a = tx.read("alice", ConsistencyLevels.CAUSAL)
				val b = tx.read("bob", ConsistencyLevels.CAUSAL)
				val c = tx.read("carol", ConsistencyLevels.CAUSAL)
				(a, b, c) match {
					case (Some(aVal), Some(bVal), Some(cVal)) =>
						assertEquals(s"after money transfer the sum can not change. alice = $aVal, bob = $bVal, carol = $cVal", 1000, aVal + bVal + cVal)
					case t =>
						fail(s"not all reads could be resolved: $t")
				}
				Some ()
			}
		}

	}







}