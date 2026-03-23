package sk.groda.mks;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mks.api.CmdRunner;
import com.mks.api.Command;
import com.mks.api.IntegrationPoint;
import com.mks.api.IntegrationPointFactory;
import com.mks.api.Option;
import com.mks.api.Session;
import com.mks.api.response.APIException;
import com.mks.api.response.Field;
import com.mks.api.response.InterruptedException;
import com.mks.api.response.Item;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;
import com.mks.api.response.impl.ItemImpl;

public class IntegrityCommands {
	private String username;
	private String server;
	private int port;
	private Session session;
	private CmdRunner cmdRunner = null;

	public static final int API_MAJOR_VERSION = 4;
	public static final int API_MINOR_VERSION = 16;

	protected static final String VERDICT = "verdict";
	protected static final String ANNOTATION = "annotation";
	private final static String SHARED_CATEGORY = "Shared Category";
	private final static String CATEGORY = "Category";
	private final static String MEANINGFUL = "meaningful";
	private final static String NON_MEANINGFUL = "nonmeaningful";
	private final static String STATE = "State";

	public Map<Date, String> labelsAndTheirTimeStamps = new HashMap<Date, String>();
	public Map<String, String> categoryPhaseMap = new HashMap<String, String>();

	public static final Logger LOGGER = LogManager.getLogger();

	/**
	 * Construct a Connection.
	 * 
	 */

	public IntegrityCommands(String server, int port, String user) {
		this.username = user;
		this.server = server;
		this.port = port;
		initiateIntegrityConnection();
	}

	private void initiateIntegrityConnection() {
		LOGGER.log(Level.INFO, "setting connection");
		IntegrationPointFactory ipf = IntegrationPointFactory.getInstance();
		try {
			IntegrationPoint integrationPoint = ipf.createLocalIntegrationPoint(API_MAJOR_VERSION, API_MINOR_VERSION);
			integrationPoint.setAutoStartIntegrityClient(true);
			session = integrationPoint.getCommonSession();
			// Setup connection
			cmdRunner = session.createCmdRunner();
		} catch (APIException e) {
			// If this fails we are in trouble...
			e.printStackTrace();
		}
		if (cmdRunner != null) {
			cmdRunner.setDefaultHostname(server);
			cmdRunner.setDefaultPort(port);
			cmdRunner.setDefaultUsername(username);
		}
		Command cmd = new Command(Command.IM, "connect");
		cmd.addOption(new Option("hostname", server));
		cmd.addOption(new Option("port", Integer.toString(port)));
		runCommand(cmd);
	}

	public synchronized Response runCommand(Command cmd) {
		Response resp = null;
		try {
			resp = cmdRunner.execute(cmd);

			return resp;
		} catch (final APIException e) {
			LOGGER.log(Level.INFO, e.getMessage(), e);
			e.printStackTrace();

		}
		return resp;
	}

	/**
	 * Disconnects the connection. Should be run once for every
	 * <tt>makeConnection(String, int)</tt> call.
	 * 
	 */
	public boolean disconnect() {

		try {
			cmdRunner.execute(new String[] { "integrity", "disconnect" });
			return true;
		} catch (APIException apiEx) {
			LOGGER.log(Level.ERROR, "APIException: " + apiEx.getClass().getName() + "\n Discmessage:     "
					+ apiEx.getMessage() + "\n DiscexceptionId: " + apiEx.getExceptionId(), apiEx);
			return false;
		}
	}

	/**
	 * View an Integrity Item
	 * 
	 * @param conn
	 * @param ID
	 * @param fields
	 */
	public Response viewItem(String ID, String... fields) {
		Command cmd = new Command(Command.IM, "issues");
		cmd.addOption(new Option("fields", makeList(fields)));
		cmd.addSelection(ID);

		return runCommand(cmd);
	}

	/**
	 * view issue along with options Showattachments and Showattachment details
	 * 
	 * @param conn
	 * @param id
	 */
	public Response viewIssue(String id) {
		Command cmd = new Command(Command.IM, "viewissue");
		cmd.addOption(new Option("showAttachments"));
		cmd.addOption(new Option("showAttachmentDetails"));
		cmd.addOption(new Option("showRichContent"));
		cmd.addSelection(id);

		return runCommand(cmd);

	}

	/**
	 * View an Integrity Item
	 * 
	 * @param conn
	 * @param ID
	 */
	public Response viewImIssues(String ID) {
		Command cmd = new Command(Command.IM, "issues");
		cmd.addSelection(ID);

		return runCommand(cmd);
	}

	/**
	 * View an Integrity field
	 * 
	 * @param fieldName
	 */
	public Response viewField(String fieldName) {
		Command cmd = new Command(Command.IM, "viewfield");
		fieldName = fieldName.trim();
		cmd.addSelection(fieldName);

		return runCommand(cmd);
	}

	/**
	 * A convenience method to make a DELIM separated list from an array
	 * 
	 * @param fields
	 * @param DELIM
	 * @return
	 */
	private String makeList(String[] fields, String DELIM) {
		StringBuffer s = new StringBuffer("");
		for (String f : fields) {
			s.append(f);
			s.append(DELIM);
		}
		return s.toString();
	}

	/**
	 * A convenience method to make a comma separated list from an array
	 * 
	 * @param fields
	 * @return
	 */
	private String makeList(String[] fields) {
		return makeList(fields, ",");
	}

	/**
	 * get field value of an item- when you know that the output would be string
	 * 
	 * @param fieldName
	 * @param id
	 */
	public String getFieldValue(String id, String fieldName) {
		String fieldValue = null;
		Command cmd = new Command(Command.IM, "viewissue");
		id = id.trim();
		cmd.addSelection(id);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		Field field = workItem.getField(fieldName);
		fieldValue = field.getValueAsString();

		return fieldValue;
	}

	/**
	 * get field value of an item- when you have unknown return types, ex.string and
	 * list
	 * 
	 * @param id
	 * @param fieldList
	 */

	@SuppressWarnings("rawtypes")
	public Map<String, String> getMultipleFieldValuesForUnknownDataTypes(String id, List<String> fieldList,
			boolean checkASOF, String asOfDate) {

		Map<String, String> issueFieldAndvalueMap = new HashMap<String, String>();
		List<String> tempFieldList = new ArrayList<String>();
		tempFieldList = fieldList;
		String fieldsString = tempFieldList.toString().replaceAll(", ", ",");
		fieldsString = removeSquareBracketsFromString(fieldsString);

		LOGGER.log(Level.INFO, "fieldsString is: " + fieldsString);
		Command cmd = new Command(Command.IM, "issues");
		cmd.addOption(new Option("--fields", fieldsString));
		if (checkASOF) {
			cmd.addOption(new Option("asOf", asOfDate));
		}
		id = id.trim();
		cmd.addSelection(id);

		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);

		for (String field : tempFieldList) {
			String fieldValue = "";
			Field issueField = workItem.getField(field);
			String dataType = issueField.getDataType();
			LOGGER.log(Level.INFO, "dataType for field: " + field + "is: " + dataType);

			if (issueField != null) {
				if (dataType != null) {

					if (dataType.equals("com.mks.api.response.List")) {
						Iterator listOfItemsUnderField = issueField.getList().iterator();
						// getting ID value in the list item
						fieldValue = iterateThroughListOverItem(listOfItemsUnderField, false, true, false);
					} else if (dataType.equals("com.mks.api.response.Item")) {
						fieldValue = issueField.getItem().getId();
					} else if (dataType.equals("java.lang.String")) {
						fieldValue = issueField.getValueAsString();
					} else if (dataType.equals("java.lang.Integer")) {
						fieldValue = issueField.getValueAsString();
					} else if (dataType.equals("java.util.Date")) {
						fieldValue = issueField.getValueAsString();
					} else if (dataType.equals("com.mks.api.response.ItemList")) {
						Iterator listOfItemsUnderField = issueField.getList().iterator();
						fieldValue = iterateThroughListOverItem(listOfItemsUnderField, false, true, false);
					} else if (dataType.equals("com.mks.api.response.ValueList")) {
						Iterator listOfItemsUnderField = issueField.getList().iterator();
						fieldValue = iterateThroughListAndGetValues(listOfItemsUnderField);
					}
				} else {
					fieldValue = issueField.getValueAsString();
				}
			}
			if (fieldValue == null) {
				fieldValue = "";
			}
			LOGGER.log(Level.INFO, "fieldValue is: " + fieldValue);
			issueFieldAndvalueMap.put(field, fieldValue);

		}

		return issueFieldAndvalueMap;
	}

	/**
	 * get field value of an item- when you have different return types, ex.string
	 * and list
	 * 
	 * @param fieldName
	 * @param id
	 */

	@SuppressWarnings("rawtypes")
	public Map<String, String> getMultipleFieldValuesForDifferentDataTypes(String id,
			Map<String, String> fieldswithReturnTypes) {

		Map<String, String> issueFieldAndvalueMap = new HashMap<String, String>();
		String fieldValue = "";
		Command cmd = new Command(Command.IM, "viewissue");
		id = id.trim();
		cmd.addSelection(id);

		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		for (String field : fieldswithReturnTypes.keySet()) {
			Field issueField = workItem.getField(field);
			if (fieldswithReturnTypes.get(field) == "String") {
				fieldValue = issueField.getValueAsString();
			} else if (fieldswithReturnTypes.get(field) == "List") {
				Iterator listOfItemsUnderField = issueField.getList().iterator();
				// getting ID value in the list item
				fieldValue = iterateThroughListOverItem(listOfItemsUnderField, false, true, false);
			} else if (fieldswithReturnTypes.get(field) == "Item") {
				fieldValue = issueField.getItem().getId();
			}
			issueFieldAndvalueMap.put(field, fieldValue);
		}

		return issueFieldAndvalueMap;
	}

	/**
	 * get field value of an item- when you know that the output would be string
	 * 
	 * @param fieldName
	 * @param id
	 */

	public Map<String, String> getMultipleFieldValues(String id, Set<String> fieldsSet) {

		Map<String, String> issueFieldAndvalueMap = new HashMap<String, String>();

		Command cmd = new Command(Command.IM, "viewissue");
		id = id.trim();
		cmd.addSelection(id);

		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		for (String field : fieldsSet) {
			Field issueField = workItem.getField(field);
			String fieldValue = issueField.getValueAsString();

			issueFieldAndvalueMap.put(field, fieldValue);
		}

		return issueFieldAndvalueMap;
	}

	/**
	 * get field value of an item-get the Item ID
	 * 
	 * @param fieldName
	 * @param id
	 */
	public String getFieldIDValue(String id, String fieldName) {
		String fieldValue = null;
		Command cmd = new Command(Command.IM, "viewissue");
		id = id.trim();
		cmd.addSelection(id);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		Field field = workItem.getField(fieldName);

		fieldValue = field.getItem().getId();

		return fieldValue;
	}

	/**
	 * get field value of a type-get the Item ID
	 * 
	 * @param fieldName
	 * @param type
	 */
	public String getTypeFieldIDValue(String type, String fieldName) {
		String fieldValue = null;
		Command cmd = new Command(Command.IM, "viewtype");
		type = type.trim();
		cmd.addSelection(type);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(type);
		Field field = workItem.getField(fieldName);

		fieldValue = field.getItem().getId();

		return fieldValue;
	}

	/**
	 * get field value of type-get Item IDs
	 * 
	 * @param fieldName
	 * @param type
	 */
	public String getTypeListFieldIDValues(String type, String fieldName) {
		String fieldValue = null;
		Command cmd = new Command(Command.IM, "viewtype");
		type = type.trim();
		cmd.addSelection(type);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(type);
		Field field = workItem.getField(fieldName);
		Iterator listOfItemsUnderField = field.getList().iterator();
		// getting ID value in the list item
		fieldValue = iterateThroughListOverItem(listOfItemsUnderField, false, true, false);
		return fieldValue.trim();
	}

	/**
	 * get field value of an item-get the Item DisplayID
	 * 
	 * @param fieldName
	 * @param id
	 */
	public String getFieldDisplayIDValue(String id, String fieldName) {
		String fieldValue = null;
		Command cmd = new Command(Command.IM, "viewissue");
		id = id.trim();
		cmd.addSelection(id);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		Field field = workItem.getField(fieldName);

		fieldValue = field.getItem().getDisplayId();

		return fieldValue;
	}

	/**
	 * get field value of an item-get the output value as string
	 * 
	 * @param fieldName
	 * @param id
	 * @param checkAsOF
	 * @param asOFdate
	 */
	public String getFieldValueAsString(String id, String fieldName, boolean checkAsOf, String asOfDate) {
		String fieldValue = null;
		Command cmd = new Command(Command.IM, "issues");
		cmd.addOption(new Option("fields", fieldName));
		if (checkAsOf) {
			cmd.addOption(new Option("asOf", asOfDate));
		}
		id = id.trim();
		cmd.addSelection(id);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		Field field = workItem.getField(fieldName);
		fieldValue = field.getValueAsString();

		return fieldValue;
	}

	/**
	 * get field value of an item which has a list under its response
	 * 
	 * @param fieldName
	 * @param id
	 */
	@SuppressWarnings("rawtypes")
	public String getFieldValueWithIterationOverItem(String id, String fieldName, boolean checkAsOf, String asOfDate) {
		String fieldValue = null;
		Command cmd = new Command(Command.IM, "issues");
		cmd.addOption(new Option("fields", fieldName));
		if (checkAsOf) {
			cmd.addOption(new Option("asOf", asOfDate));
		}
		id = id.trim();
		cmd.addSelection(id);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		Field field = workItem.getField(fieldName);
		Iterator listOfItemsUnderField = field.getList().iterator();
		fieldValue = iterateThroughListOverItem(listOfItemsUnderField, false, true, false);

		return fieldValue;
	}

	/**
	 * get field value of an item which has a list under its response
	 * 
	 * @param fieldName
	 * @param id
	 */
	@SuppressWarnings("rawtypes")
	public String getFieldValueWithIterationOverID(String id, String fieldName) {
		String fieldValue = null;
		Command cmd = new Command(Command.IM, "viewissue");
		cmd.addSelection(id);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		Field field = workItem.getField(fieldName);
		Iterator listOfItemsUnderField = field.getList().iterator();
		fieldValue = iterateThroughListOverField(listOfItemsUnderField, "ID");

		return fieldValue;
	}

	@SuppressWarnings("rawtypes")
	private String iterateThroughListAndGetValues(Iterator listOfItems) {
		ArrayList<String> allFieldValues = new ArrayList<String>();
		String listValue = null;
		if (listOfItems != null) {
			while (listOfItems.hasNext()) {

				listValue = listOfItems.next().toString();
				LOGGER.log(Level.INFO, "context value for the item uder the list is:  " + listValue);
				allFieldValues.add(listValue);
			}
			LOGGER.log(Level.INFO,
					"all values from item tag are:" + removeSquareBracketsFromString(allFieldValues.toString()));
			return removeSquareBracketsFromString(allFieldValues.toString());
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	private String iterateThroughListOverItem(Iterator listOfItems, boolean context, boolean ID,
			boolean doNotIncludeSameIds) {
		ArrayList<String> allFieldValues = new ArrayList<String>();
		String listValue = null;
		if (listOfItems != null) {
			while (listOfItems.hasNext()) {
				ItemImpl itemInIterator = null;
				itemInIterator = (ItemImpl) listOfItems.next();
				if (context) {
					listValue = itemInIterator.getContext();
					LOGGER.log(Level.INFO, "context value for the item uder the list is:  " + listValue);
				}
				if (ID) {
					listValue = itemInIterator.getId().trim();
					LOGGER.log(Level.INFO, "ID value for the item uder the list is:  " + listValue);
				}

				if (doNotIncludeSameIds) {
					if (!allFieldValues.contains(listValue)) {
						allFieldValues.add(listValue);
					}

				} else {
					allFieldValues.add(listValue);
				}
			}
			LOGGER.log(Level.INFO,
					"all values from item tag are:" + removeSquareBracketsFromString(allFieldValues.toString()));
			return removeSquareBracketsFromString(allFieldValues.toString());
		}
		return null;
	}

	public String convertDateToStringFormat1(Date dateValue) {

		// SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy h:mm:ss a");
		SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy hh:mm:ss a", Locale.US);
		String convertedDate = formatter.format(dateValue);
		LOGGER.log(Level.INFO, "as of date is: " + convertedDate);
		return convertedDate;
	}

	public String convertDateToStringFormat2(Date dateValue) {

		// SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
		String convertedDate = formatter.format(dateValue);
		LOGGER.log(Level.INFO, "as of date is: " + convertedDate);
		return convertedDate;
	}

	@SuppressWarnings("rawtypes")
	private String iterateThroughListOverField(Iterator listOfItems, String fieldName) {
		ArrayList<String> allFieldValues = new ArrayList<String>();
		String listFieldValue = null;
		if (listOfItems != null) {
			while (listOfItems.hasNext()) {
				ItemImpl itemInIterator = null;
				itemInIterator = (ItemImpl) listOfItems.next();
				listFieldValue = itemInIterator.getField(fieldName).getString();
				LOGGER.log(Level.INFO, "field value for the item uder the list is:  " + listFieldValue);

				allFieldValues.add(listFieldValue);
			}
			LOGGER.log(Level.INFO, "all files under the field " + fieldName + "are:"
					+ removeSquareBracketsFromString(allFieldValues.toString()));
			return removeSquareBracketsFromString(allFieldValues.toString());
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	private Map<String, Date> iterateThroughListOverLabels(Iterator listOfItems) {

		String labelName = null;
		Date time = null;

		Map<String, Date> labelsWithTime = new HashMap<String, Date>();
		if (listOfItems != null) {
			while (listOfItems.hasNext()) {
				ItemImpl itemInIterator = null;
				itemInIterator = (ItemImpl) listOfItems.next();
				labelName = itemInIterator.getField("Label").getValueAsString();
				time = itemInIterator.getField("AsOf").getDateTime();
				labelsWithTime.put(labelName, time);

			}

			return labelsWithTime;
		}
		return null;
	}

	public String removeSquareBracketsFromString(String checkString) {
		if (checkString.indexOf('[') >= 0 && checkString.lastIndexOf(']') > 0) {
			return checkString.substring(checkString.indexOf('[') + 1, checkString.lastIndexOf(']'));
		}
		return null;
	}

	public static String removeAnySquareBracketsFromString(String checkString) {
		if (checkString.indexOf('[') >= 0 && checkString.lastIndexOf(']') > 0) {
			return checkString.substring(checkString.indexOf('[') + 1, checkString.lastIndexOf(']'));
		}

		if (checkString.indexOf('[') >= 0) {
			return checkString.substring(checkString.indexOf('[') + 1);
		}

		if (checkString.lastIndexOf(']') > 0) {
			return checkString.substring(0, checkString.lastIndexOf(']'));
		}

		return null;
	}

	/**
	 * view document differences
	 * 
	 * @param segmentId1
	 * @param segmentId2
	 * @return
	 */
	public void viewSegmentDifferences(String segmentId1, String segmentId2, boolean showAllItems, boolean validatedBy,
			boolean iD, boolean category) {
		Command cmd = new Command(Command.IM, "diffsegments");
		cmd.addOption(new Option("g"));
		if (!showAllItems) {
			cmd.addOption(new Option("hideItemsWithoutDifferences"));
		}
		if (validatedBy) {
			cmd.addOption(new Option("fields=Validated By"));
		}
		if (iD) {
			cmd.addOption(new Option("fields=ID"));
		}
		if (category) {
			cmd.addOption(new Option("fields=Category"));
		}
		cmd.addSelection(segmentId1);
		cmd.addSelection(segmentId2);
		runCommand(cmd);
	}

	/**
	 * get field value of an item-get the output value as Date
	 * 
	 * @param dateFieldName
	 * @param id
	 */
	public Date getFieldValueAsDate(String id, String dateFieldName) {
		Date fieldValue = null;
		Command cmd = new Command(Command.IM, "issues");
		cmd.addOption(new Option("fields", dateFieldName));
		id = id.trim();
		cmd.addSelection(id);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		Field field = workItem.getField(dateFieldName);
		fieldValue = field.getDateTime();

		return fieldValue;
	}

	@SuppressWarnings("rawtypes")
	public Map<String, Date> getLabelsAndTime(String id) {

		Command cmd = new Command(Command.IM, "viewissue");
		cmd.addOption(new Option("ShowLabels"));
		cmd.addSelection(id);
		Response response = runCommand(cmd);
		WorkItem workItem = response.getWorkItem(id);
		Field field = workItem.getField("MKSIssueLabels");
		Iterator listOfItemsUnderField = field.getList().iterator();

		return iterateThroughListOverLabels(listOfItemsUnderField);
	}

	/**
	 * creates segment
	 * 
	 * @param type
	 * @param project
	 * @param title
	 * @param assignedUser
	 * @return Item
	 */
	public Item createSegment(String type, String project, String title, String assignedUser) {
		Command cmd = new Command(Command.IM, "createsegment");
		cmd.addOption(new Option("type=" + type));
		cmd.addOption(new Option("field=Document Short Title=" + title));
		cmd.addOption(new Option("field=Project=" + project));
		cmd.addOption(new Option("field=Assigned User=" + assignedUser));
		Response response = runCommand(cmd);
		try {
			return response.getResult().getPrimaryValue();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * creates content
	 * 
	 * @param type
	 * @param category
	 * @param text
	 * @param parentId
	 * @return Item
	 */
	public Item createContent(String type, String category, String text, String parentId) {
		Command cmd = new Command(Command.IM, "createcontent");
		cmd.addOption(new Option("type=" + type));
		cmd.addOption(new Option("field=Category=" + category));
		cmd.addOption(new Option("field=Text=" + text));
		cmd.addOption(new Option("parentID=" + parentId));
		Response response = runCommand(cmd);
		try {
			return response.getResult().getPrimaryValue();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * edit issue for single field
	 * 
	 * @param fieldName
	 * @param fieldValue
	 * @return Item
	 */
	public Response editIssue(String issueId, String fieldName, String fieldValue) throws APIException {
		Command cmd = new Command(Command.IM, "editissue");
		cmd.addOption(new Option("field=" + fieldName + "=" + fieldValue));
		cmd.addSelection(issueId);
		Response response = runCommand(cmd);
		return response;
	}

	/**
	 * edit issue for multiple fields
	 * 
	 * @param issueId
	 * @param fieldNameAndValueMap
	 * @return Item
	 */

	public Response editIssueWithMultipleFields(String issueId, Map<String, String> fieldNameAndValueMap)
			throws APIException {
		Command cmd = new Command(Command.IM, "editissue");

		for (String key : fieldNameAndValueMap.keySet()) {

			cmd.addOption(new Option("field=" + key + "=" + fieldNameAndValueMap.get(key)));

		}
		cmd.addSelection(issueId);

		Response response = runCommand(cmd);

		return response;
	}

	public Response query(String queryDefinition, String fields) {
		Command cmd = new Command(Command.IM, "issues");
		cmd.addOption(new Option("queryDefinition", queryDefinition));
		cmd.addOption(new Option("fields", fields));
		return runCommand(cmd);
	}

	public Response getChildItems(String id) throws APIException {

		Command cmd = new Command(Command.IM, "issues");
		cmd.addOption(new Option("queryDefinition", "(walkdocordered[" + id + "])"));
		return runCommand(cmd);
	}

	public Response getChildItems(String id, boolean checkAsOF, String date) throws APIException {

		/*
		 * Command cmd = new Command(Command.IM, "issues"); cmd.addOption(new
		 * Option("queryDefinition", "(walkdocordered[" + id + "])"));
		 */
		Command cmd = new Command(Command.IM, "viewsegment");
		cmd.addOption(new Option("fields", "ID,State"));
		if (checkAsOF) {
			cmd.addOption(new Option("asOf", date));
		}
		cmd.addSelection(id);
		return runCommand(cmd);
	}

	@SuppressWarnings("rawtypes")
	private Map<String, String> iterateThroughListandGetFieldValues(Iterator listOfItems, String field) {

		Map<String, String> idAndFieldValue = new HashMap<String, String>();
		String idValue = null;
		String fieldValue = null;
		if (listOfItems != null) {
			while (listOfItems.hasNext()) {
				ItemImpl itemInIterator = null;
				itemInIterator = (ItemImpl) listOfItems.next();

				idValue = itemInIterator.getId();
				LOGGER.log(Level.INFO, "ID value for the item uder the list is:  " + idValue);
				fieldValue = itemInIterator.getField(field).getValueAsString();
				LOGGER.log(Level.INFO, "ID value for the item uder the list is:  " + fieldValue);
				idAndFieldValue.put(idValue, fieldValue);
			}
			LOGGER.log(Level.INFO, "The ID and value map is :  " + idAndFieldValue.toString());

			return idAndFieldValue;
		}
		return null;
	}

	/**
	 * checks
	 * 
	 * @param field
	 * @param expected field type
	 * @return true or false
	 */

	public boolean checkGivenFieldType(String fieldName, String expectedFieldType) {
		String typeFieldValue = null;
		Response fieldResponse = viewField(fieldName);
		WorkItemIterator itemIterator = fieldResponse.getWorkItems();
		if (itemIterator != null && itemIterator.hasNext()) {
			try {
				WorkItem item = itemIterator.next();
				Field issueField = item.getField("type");
				typeFieldValue = issueField.getValueAsString();

			} catch (APIException e) {
				LOGGER.log(Level.ERROR, e.getMessage(), e);
			}

		}

		if (typeFieldValue != null && expectedFieldType.equals(typeFieldValue)) {
			return true;
		} else
			return false;

	}

	@SuppressWarnings("rawtypes")
	public Map<String, String> getCategoryMap(String categoryField) {

		Map<String, String> pickValuesWithPhaseValue = new HashMap<String, String>();
		Response fieldResponse = viewField(categoryField);
		WorkItemIterator itemIterator = fieldResponse.getWorkItems();
		if (itemIterator != null && itemIterator.hasNext()) {
			try {
				WorkItem item = itemIterator.next();
				Field issueField = item.getField("picks");
				Iterator listOfItemsUnderField = issueField.getList().iterator();
				pickValuesWithPhaseValue = iterateThroughListandGetFieldValues(listOfItemsUnderField, "phase");
			} catch (APIException e) {
				LOGGER.log(Level.ERROR, e.getMessage(), e);
				return null;
			}
			return pickValuesWithPhaseValue;
		}
		return null;
	}

	public Map<String, String> getAllowedTypesFromRelationshipField(String relationShipField) {

		Map<String, String> allowedTypes = new HashMap<String, String>();
		Response fieldResponse = viewField(relationShipField);
		WorkItemIterator itemIterator = fieldResponse.getWorkItems();
		if (itemIterator != null && itemIterator.hasNext()) {
			try {
				WorkItem item = itemIterator.next();
				Field issueField = item.getField("allowedTypes");
				@SuppressWarnings("rawtypes")
				Iterator listOfItemsUnderField = issueField.getList().iterator();
				String fromValue = null;

				String typeValues = null;
				if (listOfItemsUnderField != null) {
					while (listOfItemsUnderField.hasNext()) {
						ItemImpl itemInIterator = null;
						itemInIterator = (ItemImpl) listOfItemsUnderField.next();
						List<String> toValueList = new ArrayList<String>();
						fromValue = itemInIterator.getId();
						LOGGER.log(Level.INFO, "to value for the allowed type is:  " + fromValue);
						Field listField = itemInIterator.getField("to");
						Iterator listofItemsUnderSubField = listField.getList().iterator();
						String tempValue = null;
						if (listofItemsUnderSubField != null) {

							while (listofItemsUnderSubField.hasNext()) {
								ItemImpl subItemInIterator = null;
								subItemInIterator = (ItemImpl) listofItemsUnderSubField.next();
								tempValue = subItemInIterator.getId();
								toValueList.add(tempValue);
							}
						}

						allowedTypes.put(fromValue, toValueList.toString());
					}

				}
				LOGGER.log(Level.INFO, "The from and to map for allowed types is :  " + allowedTypes.toString());
			} catch (APIException e) {
				LOGGER.log(Level.ERROR, e.getMessage(), e);
				return null;
			}
			return allowedTypes;
		}
		return null;

	}

	public static List<String> convertStringToArrayList(String givenString, String seperator) {
		List<String> newArrayList = new ArrayList<String>();
		String tempStringArray[] = givenString.split(seperator);

		List<String> tempList = Arrays.asList(tempStringArray);
		// spaces are included above, need to be taken out
		for (int k = 0; k < tempList.size(); k++) {
			LOGGER.log(Level.INFO, "string being checked is: " + tempList.get(k).trim());
			String newString = removeAnySquareBracketsFromString(tempList.get(k).trim());
			if (newString != null) {
				newArrayList.add(newString);
			} else {
				newArrayList.add(tempList.get(k).trim());
			}

		}
		return newArrayList;
	}

	public static <T> List<T> removeDuplicates(List<T> list) {

		// Create a new ArrayList
		List<T> newList = new ArrayList<T>();

		// Traverse through the first list
		for (T element : list) {

			// If this element is not present in newList
			// then add it
			if (!newList.contains(element)) {

				newList.add(element);
			}
		}

		// return the new list
		return newList;
	}


}
