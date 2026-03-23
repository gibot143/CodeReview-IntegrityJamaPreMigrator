package sk.groda.jdbc;

import java.sql.SQLException;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

public class DataBaseConnection {

	private final static String CONN_FACTORY_CLASS_NAME = "oracle.jdbc.pool.OracleDataSource";
	public static PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();;

	public static void createDataSource(String dburl, String dbuser, String dbpassword) throws SQLException {
		pds.setConnectionFactoryClassName(CONN_FACTORY_CLASS_NAME);
		pds.setUser(dbuser);
		pds.setPassword(dbpassword);
		pds.setURL(dburl);
		pds.setConnectionPoolName("JamaPremigrator");
		pds.setInitialPoolSize(4);
		pds.setMaxPoolSize(5);
		pds.setTimeoutCheckInterval(10);
		pds.setInactiveConnectionTimeout(60);
	}

}
