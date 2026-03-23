package sk.groda.exporter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;

import sk.groda.jdbc.DataBaseConnection;
import sk.groda.mks.IntegrityCommands;

public class Main {

	private static final Logger LOGGER = LogManager.getLogger(Main.class);

	private static Properties dbprops = new Properties();
	private static Properties mksprops = new Properties();
	public static IntegrityCommands cmd;
	private static ExecutorService service = Executors.newFixedThreadPool(4);

	public static void main(String[] args) {
		try {
			String[] ids = init();
			for (String id : ids) {

				Response response = cmd.getChildItems(id);
				process(response, id);
			}
			service.shutdown();
		} catch (APIException e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
		} catch (FileNotFoundException e) {
			LOGGER.log(Level.ERROR, "Error reading properties file", e);
		} catch (IOException e) {
			LOGGER.log(Level.ERROR, "Error initializing connection to mks servers", e);
		} catch (SQLException e) {
			LOGGER.log(Level.ERROR, "Error initializing connection to database servers", e);
		}
	}

	/**
	 * 
	 * initialize connections integrity and oracle database
	 * 
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws SQLException
	 */
	private static String[] init() throws FileNotFoundException, IOException, SQLException {
		dbprops.load(new FileInputStream("jdbc.properties"));
		mksprops.load(new FileInputStream("mks.properties"));

		cmd = new IntegrityCommands(mksprops.getProperty("hostname"), Integer.parseInt(mksprops.getProperty("port")),
				mksprops.getProperty("username"));
		DataBaseConnection.createDataSource(dbprops.getProperty("db.url"), dbprops.getProperty("db.username"),
				dbprops.getProperty("db.password"));

		return mksprops.getProperty("documentids").split(",");
	}

	/**
	 * 
	 * process individual integrity issue and same the metadata to staging database
	 * 
	 * @param response
	 * @param id
	 * @throws APIException
	 */
	private static void process(Response response, String id) throws APIException {

		LOGGER.log(Level.DEBUG, "Document {} has {} items", id, response.getWorkItemListSize());
		WorkItemIterator wii = response.getWorkItems();
		while (wii.hasNext()) {
			WorkItem wi = wii.next();
			String childid = wi.getField("ID").getValueAsString();
			Runnable worker = new Helper(childid);
			service.execute(worker);
		}
	}
}
