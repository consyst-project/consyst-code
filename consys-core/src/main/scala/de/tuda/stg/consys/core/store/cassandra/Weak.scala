package de.tuda.stg.consys.core.store.cassandra

import com.datastax.oss.driver.api.core.{ConsistencyLevel => CLevel}
import de.tuda.stg.consys.core.store.cassandra.Strong.StoreType
import de.tuda.stg.consys.core.store.{StoreConsistencyLevel, StoreConsistencyModel}

import scala.reflect.runtime.universe._

/**
 * Created on 11.12.19.
 *
 * @author Mirko Köhler
 */
case object Weak extends StoreConsistencyLevel {
	override type StoreType = CassandraStore
	override def toModel(store : StoreType) : StoreConsistencyModel {type StoreType = Weak.this.StoreType} = new Model(store)

	private class Model(val store : CassandraStore) extends StoreConsistencyModel {
		override type StoreType = CassandraStore

		override def toLevel : StoreConsistencyLevel = Weak

		override def replicateRaw[T <: StoreType#ObjType : TypeTag](addr : StoreType#Addr, obj : T, txContext : StoreType#TxContext) : StoreType#RawType[T] = {
			val cassObj = new WeakCassandraObject(addr, obj, store, txContext)
			cassObj
		}

		override def lookupRaw[T <: StoreType#ObjType : TypeTag](addr : StoreType#Addr, txContext : StoreType#TxContext) : StoreType#RawType[T] = {
			val raw = store.CassandraBinding.readObject[T](addr, CLevel.ONE)
			new WeakCassandraObject(addr, raw, store, txContext)
		}
	}


	private class WeakCassandraObject[T <: java.io.Serializable : TypeTag](override val addr : String, override val state : T, store : StoreType, txContext : StoreType#TxContext) extends CassandraObject[T] {
		override def consistencyLevel : StoreConsistencyLevel { type StoreType = CassandraStore } = Weak

		override def writeToStore(store : CassandraStore) : Unit =
			store.CassandraBinding.writeObject(addr, state, CLevel.ONE, txContext.timestamp)
	}
}