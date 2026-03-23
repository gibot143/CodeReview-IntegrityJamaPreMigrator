package sk.groda.exporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mks.api.im.IMModelTypeName;
import com.mks.api.response.APIException;
import com.mks.api.response.Field;
import com.mks.api.response.Item;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;

import sk.groda.jdbc.DataBaseConnection;

public class Helper extends Main implements Runnable {

	private String id;

	private static final Logger LOGGER = LogManager.getLogger(Helper.class);

	public Helper(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public void run() {
		Response wimetadata = cmd.viewIssue(id);
		try {
			if (wimetadata.getExitCode() > 0)
				LOGGER.log(Level.ERROR, "issue while retriveing metadata from {}", id);
			writetojson(wimetadata);
		} catch (APIException e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
		}
	}

	/**
	 * 
	 * Write integrity metadata to a json file
	 * 
	 * @param wimetadata
	 * @throws APIException
	 */
	private static void writetojson(Response wimetadata) throws APIException {
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> data = new HashMap<>();
		WorkItemIterator wii = wimetadata.getWorkItems();
		while (wii.hasNext()) {
			WorkItem wi = wii.next();
			Iterator<Field> fielditr = wi.getFields();
			while (fielditr.hasNext()) {
				Field field = fielditr.next();
				Object childobject = getFieldValue(field);
				data.put(field.getName(), childobject);
			}
		}
		try {
			Path path = Paths.get("target", "export", data.get("ID") + ".json");
			if (!Files.exists(path.getParent())) {
				Files.createDirectory(path.getParent());
			}
			File file = new File(String.valueOf(path.toString()));
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
			LOGGER.log(Level.DEBUG, "Completed writing data to {}", path.getFileName());
			logInformationinDatabase(data, path);
		} catch (JsonProcessingException e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
		} catch (IOException e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
		}
	}

	/**
	 * 
	 * log integrityid, documentid, hasattachments, hastextattachments, hassharedattachments, bookmarks information to db.
	 * 
	 * @param data
	 * @param path
	 */
	private static void logInformationinDatabase(Map<String, Object> data, Path path) {
		try (Connection conn = DataBaseConnection.pds.getConnection();
				PreparedStatement stmt = conn
						.prepareStatement("insert into migrationdata values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");) {
			int i=1;
			stmt.setInt(i++, (int) data.get("ID"));
			if (data.containsKey("Document ID"))
				stmt.setInt(i++, Integer.parseInt((String) data.get("Document ID")));
			else
				stmt.setNull(i++, Types.INTEGER);
			stmt.setNull(i++, Types.INTEGER);
			stmt.setNull(i++, Types.INTEGER);
			stmt.setString(i++, path.toAbsolutePath().toString());
			stmt.setInt(i++,
					data.get("Attachments") != null && ((Collection<?>) data.get("Attachments")).size() > 0 ? 1 : 0);
			stmt.setInt(i++,
					data.get("Text Attachments") != null && ((Collection<?>) data.get("Text Attachments")).size() > 0
							? 1
							: 0);
			stmt.setInt(i++, data.get("Shared Attachments") != null
					&& ((Collection<?>) data.get("Shared Attachments")).size() > 0 ? 1 : 0);
			String bookmarks = cleanbookmarks((String) data.get("Referenced Bookmarks"));
			if (bookmarks == null)
				stmt.setNull(i++, Types.VARCHAR);
			else
				stmt.setString(i++, bookmarks);
			stmt.setNull(i++, Types.BLOB);
			stmt.execute();
		} catch (SQLException e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
		}
	}

	/**
	 * 
	 * strip unnecessary data from integrity bookmarks and return comma separated values of bookmark names
	 * 
	 * @param object
	 * @return
	 */
	private static String cleanbookmarks(String object) {
		if (object == null)
			return null;
		List<String> list = Arrays.asList(object.split(","));
		List<String> processedbookmarks = new ArrayList<>();
		list.forEach(b -> {
			if (b.contains("="))
				processedbookmarks.add(b.split("=")[1].strip());
			else
				processedbookmarks.add(b.strip());
		});
		return String.join(",", processedbookmarks);
	}

	/**
	 * 
	 * Get value of integrity item based on type of field value
	 * 
	 * @param field
	 * @return
	 */
	private static Object getFieldValue(Field field) {
		String datatype = field.getDataType();
		if (datatype == null)
			return null;
		LOGGER.log(Level.DEBUG, "{} {} {}", field.getName(), datatype, field.getValue());
		switch (datatype) {
		case "com.mks.api.response.Item":
			return field.getItem().getId();
		case "com.mks.api.response.ItemList":
			Set<Object> itemlist = new HashSet<>();
			List<Item> list = field.getList();
			for (Item item : list) {
				if (item.getModelType().equals(IMModelTypeName.ATTACHMENT)) {
					Map<String, Object> att_data = new HashMap<>();
					Field attname = item.getField("FileName");
					att_data.put(attname.getName(), attname.getValue());
					Field attsize = item.getField("FileSize");
					att_data.put(attsize.getName(), attsize.getValue());
					itemlist.add(att_data);
				} else {
					itemlist.add(item.getId());
				}
			}
			return itemlist;
		default:
			return field.getValue();
		}
	}

}
