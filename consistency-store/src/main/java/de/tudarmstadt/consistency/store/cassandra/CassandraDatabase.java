package de.tudarmstadt.consistency.store.cassandra;

import com.datastax.driver.core.*;
import de.tudarmstadt.consistency.checker.qual.Strong;
import de.tudarmstadt.consistency.checker.qual.Weak;
import de.tudarmstadt.consistency.store.Handle;
import de.tudarmstadt.consistency.store.StateEvent;
import de.tudarmstadt.consistency.store.Store;
import de.tudarmstadt.consistency.utils.Log;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.*;


/**
 * Created on 18.05.18.
 *
 * @author Mirko Köhler
 */

public class CassandraDatabase implements Store<UUID, StateEvent>, AutoCloseable {

	private final static String DEFAULT_KEYSPACE = "keyspace_consistency";
	private final static String DEFAULT_TABLE_NAME = "table_data";

	//The host address where to contact the database
	private final String address;
	//The port where to contact the database
	private final int port;

	private final Cluster cluster;
	private final Session session;

	private final ObjectTable table;

	private CassandraDatabase(String address, int port, String keyspaceName, int replicationFactor) {
		this.address = address;
		this.port = port;

		//Initialize a new keyspace


		cluster = Cluster.builder().addContactPoint(address).withPort(port).build();
		Session localSession = cluster.newSession();

		//Strategy NetworkTopologyStrategy does not support a replication factor.
		String replicationStrategy = "SimpleStrategy";

		localSession.execute("DROP KEYSPACE IF EXISTS " + keyspaceName);
		localSession.execute("CREATE KEYSPACE " +
				keyspaceName + " WITH replication = {" +
				"'class':'" + replicationStrategy + "'," +
				"'replication_factor':" + replicationFactor +
				"};"
		);

		session = cluster.connect(keyspaceName);


		//Initialize the data table
		this.table = new ObjectTable(DEFAULT_TABLE_NAME);
		this.table.initialize();

	}

	public static CassandraDatabase create(String hostname, int port) {
		CassandraDatabase database = new CassandraDatabase(hostname, port, DEFAULT_KEYSPACE, 3);
		return database;
	}

	public static CassandraDatabase local() {

		//Other possible address: localhost
		return create("127.0.0.1", 9042);
	}

	@Override
	@SuppressWarnings("consistency")
	public <T> CassandraHandle<T> obtain(UUID id, Class<? extends T> valueClass, Class<?> consistencyLevel) {

		if (Objects.equals(consistencyLevel, Weak.class)) {
			return new CassandraHandle.WeakHandle<T>(id, this);
		} else if (Objects.equals(consistencyLevel, Strong.class)) {
			return new CassandraHandle.StrongHandle<T>(id, this);
		}

		throw new IllegalArgumentException("unknown consistency level");
	}


	@Override
	public void close() {
		session.close();
		cluster.close();
	}

	public ResultSet execute(String query) {
		Log.info(CassandraDatabase.class, "executing query:\t" + query);
		return session.execute(query);
	}

	public ResultSet execute(Statement statement) {
		Log.info(CassandraDatabase.class, "executing query:\t" + statement);
		return session.execute(statement);
	}


	ObjectTable getTable() {
		return table;
	}



	class ObjectTable {

		private final String tableName;

		private ObjectTable(String tableName) {
			this.tableName = tableName;
		}

		public void initialize() {

			session.execute("DROP TABLE IF EXISTS " + tableName );
			session.execute("CREATE TABLE " + tableName + " (" +
					getKeyName() + " uuid PRIMARY KEY, " +
					getDataName() + " blob);");
		}


		public byte[] readWithConsistencyLevel(UUID key, ConsistencyLevel consistencyLevel) {

			//Retrieve all elements with key from the database
			ResultSet result = execute(
					select().from(getTableName())
							.where(eq(getKeyName(), key))
							.setConsistencyLevel(consistencyLevel)
			);

			List<Row> rows = result.all();

			if (rows.isEmpty()) {
				return null;
			} else if (rows.size() > 1) {
				throw new IllegalStateException("can not retrieve more than 1 row, but got:\n" + rows);
			}
			//else rows.size() == 1

			ByteBuffer buffer = rows.get(0).get(table.getDataName(), ByteBuffer.class);


			//The read array does contain no link to the database in handles.
			//Set the databases of handles when the object has been de-serialized.
			return buffer.array();

		}


		public void writeWithConsistencyLevel(UUID key, byte[] bytes, ConsistencyLevel consistencyLevel) {
			ByteBuffer data = ByteBuffer.wrap(bytes);

			//Store object in database
			session.execute(
					//Upsert operation: if the row already exists, then it is updated. Does not provide any concurrency control.
					insertInto(getTableName())
							.values(new String[]{getKeyName(), getDataName()}, new Object[]{key, data})
							.setConsistencyLevel(consistencyLevel)
			);

		}




		public String getTableName() {
			return tableName;
		}

		public String getKeyName() {
			return "id";
		}

		public String getDataName() {
			return "data";
		}

	}

}