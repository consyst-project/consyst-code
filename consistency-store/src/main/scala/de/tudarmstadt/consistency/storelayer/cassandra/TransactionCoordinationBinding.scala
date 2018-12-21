package de.tudarmstadt.consistency.storelayer.cassandra

import com.datastax.driver.core.ConsistencyLevel
import com.datastax.driver.core.querybuilder.QueryBuilder

/**
	* Created on 21.12.18.
	*
	* @author Mirko Köhler
	*/
trait TransactionCoordinationBinding[Id, TxStatus, Isolation] {
	self : SessionBinding[Id, _, _, TxStatus, Isolation, _] with TxStatusBindings[TxStatus] =>
	import typeBinding._

	private val casTxTableName : String = "t_cas_tx"


	/* queries */
	def CREATE_CAS_TX_TABLE(): Unit = {
		session.execute(
			s"""CREATE TABLE $casTxTableName
				 | (txid ${TypeCodecs.Id.getCqlType.asFunctionParameterString},
				 | status ${TypeCodecs.TxStatus.getCqlType.asFunctionParameterString},
				 | isolation ${TypeCodecs.Isolation.getCqlType.asFunctionParameterString},
				 | PRIMARY KEY(txid))""".stripMargin
		)
	}


	/* operations */
	def addNewTransaction(txid : Id, txStatus : TxStatus, isolation : Isolation) : Boolean = {
		import com.datastax.driver.core.querybuilder.QueryBuilder._

		val txInsertResult = session.execute(
			insertInto(casTxTableName)
				.values(
					Array[String]("txid", "status", "isolation"),
					Array[AnyRef](txid.asInstanceOf[AnyRef], txStatus.asInstanceOf[AnyRef], isolation.asInstanceOf[AnyRef])
				)
				.ifNotExists()
				.setConsistencyLevel(ConsistencyLevel.ALL)
		)

		val txInsertResultRow = txInsertResult.one()
		assert(txInsertResultRow != null)

		//1.a. If the transaction id was already in use abort!
		txInsertResult.wasApplied()
	}

	def abortIfPending(txid : Id) : Boolean = {
		import com.datastax.driver.core.querybuilder.QueryBuilder._

		val updateOtherTxResult = session.execute(
			update(casTxTableName)
				.`with`(set("status", TxStatus.ABORTED))
				.where(QueryBuilder.eq("txid", txid))
				.onlyIf(QueryBuilder.ne("status", TxStatus.COMMITTED))
				.setConsistencyLevel(ConsistencyLevel.ALL)
		)

		updateOtherTxResult.wasApplied()
	}

	def commitIfPending(txid : Id) : Boolean = {
		import com.datastax.driver.core.querybuilder.QueryBuilder._

		val updateOtherTxResult = session.execute(
			update(casTxTableName)
				.`with`(set("status", TxStatus.COMMITTED))
				.where(QueryBuilder.eq("txid", txid))
				.onlyIf(QueryBuilder.ne("status", TxStatus.ABORTED))
				.setConsistencyLevel(ConsistencyLevel.ALL)
		)

		updateOtherTxResult.wasApplied()
	}


}