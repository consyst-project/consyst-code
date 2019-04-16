package de.tudarmstadt.consistency.replobj.actors

import de.tudarmstadt.consistency.replobj.{ConsistencyLevel, TransactionalReplicaSystem}

import scala.util.DynamicVariable

/**
	* Created on 16.04.19.
	*
	* @author Mirko Köhler
	*/
trait AkkaTransactionalReplicaSystem[Addr] extends TransactionalReplicaSystem[Addr, Transaction] {

	private val threadContext : DynamicVariable[TransactionContext] = new DynamicVariable(null)

	private def context : TransactionContext = {
		if (threadContext.value == null)
			threadContext.value = new TransactionContext

		threadContext.value
	}

	override def clearTransaction() : Unit = {
		if (threadContext.value != null) {
			threadContext.value.clear()
			threadContext.value = null
		}
	}

	override def hasCurrentTransaction : Boolean = context.hasCurrentTransaction

	override def getCurrentTransaction : Transaction = context.getCurrentTransaction

	override def newTransaction(consistencyLevel : ConsistencyLevel) : Unit =
		context.newTransaction(consistencyLevel)

	override def commitTransaction() : Unit =
		context.commitTransaction()

	override def setCurrentTransaction(tx : Transaction) : Unit =
		context.setCurrentTransaction(tx)
}
