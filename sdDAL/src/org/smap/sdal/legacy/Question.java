/*****************************************************************************

This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

 ******************************************************************************/

package org.smap.sdal.legacy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.constants.SmapQuestionTypes;
import org.smap.sdal.model.AuditItem;
import org.smap.sdal.model.KeyValueSimp;
import org.smap.sdal.model.SetValue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/*
 * Class to store Question objects
 * The class handles Create, Read, Update and Delete to/from a database. However
 *  generally Question objects will be created by an external class that populates
 *  question objects from a result set.
 */

public class Question {

	private static Logger log =
			 Logger.getLogger(Question.class.getName());

	// Database Attributes
	private int q_id;

	private int seq = -1;
	
	private int l_id;
	
	private String listname;

	private boolean published;

	private String name;
	
	private String column_name;

	private String qType = "string";
	
	private String dataType;

	private String question;

	private String qtext_id;
	
	private String defaultAnswer;
	
	public ArrayList<SetValue> setValues;

	private String info;			// hint
	
	private String infotext_id;

	private boolean visible = false;
	
	private String source;
	
	private String source_param;
	
	private boolean readOnly = false;
	
	private String readOnlyExpression;
	
	private boolean mandatory = false;
	
	private boolean compressed = false;
	
	private String relevant;

	private String calculate;
	
	private String constraint;
	
	private String constraint_msg;
	
	private String required_expression;
	
	private String appearance;
	
	private String parameters;
	
	private String path = null;	// Xpath to this question (use only when loading from xform)
	
	private String relativePath;	// Path within the form
	
	private String nodeset;	// Nodeset for cascading selects
	
	private String nodeset_value;
	
	private String nodeset_label;
	
	private int f_id;
	
	private String autoplay;
	
	private String trigger;
	
	private String accuracy;
	
	private String intent;

	// Other attributes
	private boolean repeatCount = false;	// Set true if this is a dummy calculate generated by pyxform to hold a repeat count
	
	public Vector<String> singleChoiceOptions = null;

	private String formRef; // Unique survey reference to form containing this question

	public String qSubType; // No longer written to database
	
	public String qGroupBeginRef; // Set if this is a dummy question marking the end of a group
	
	Collection<Option> choices = null;
	
	public int oSeq = 0;				// A sequence counter for options	
	
	public boolean isTableList = false;
	
	//public HashMap<String, String> filters = null;
	
	/*
	 * Constructor Establish Database Connection using JNDI
	 */
	public Question() {
	}

	/*
	 * Getters
	 */
	public int getId() {
		return q_id;
	}
	
	public int getFormId() {
		return f_id;
	}

	public int getSeq() {
		return seq;
	}
	
	public int getListId() {
		return l_id;
	}
	
	public String getListName() {
		return listname;
	}
	
	public boolean isPublished() {
		return published;
	}

	public String getName() {
		return name;
	}
	
	public String getColumnName(boolean isReference) {
		String value = column_name;
		if(isReference && value != null && value.startsWith("_")) {
			// remove leading underscore
			value = value.substring(1);
		}
		return value;
	}

	public String getType() {
		if(qType.equals("chart") || qType.equals(SmapQuestionTypes.CHILD_FORM) || qType.equals("parent_form")) {
			return "string";
		} else {
			return qType;
		}
	}
	
	public String getDataType() {
		return dataType;
	}

	public String getSubType() {
		return qSubType;
	}

	public String getQuestion() {
		return question;
	}
	
	public String getQTextId() {
		return qtext_id;
	}

	public String getDefaultAnswer() {
		return defaultAnswer;
	}

	public String getInfo() {
		return info;
	}
	
	public String getInfoTextId() {
		return infotext_id;
	}

	public boolean isVisible() {
		return visible;
	}
	
	public String getSource() {
		return source;
	}
	
	public String getSourceParam() {
		return source_param;
	}
	
	public boolean isReadOnly() {
		if(qType.equals("chart")) {
			return true;
		} else {
			return readOnly;
		}
	}
	
	public String getReadOnlyExpression() {
		return readOnlyExpression;
	}
	
	public boolean isRepeatCount() {
		return repeatCount;
	}

	public boolean isMandatory() {
		return mandatory;
	}
	
	public boolean isCompressed() {
		return compressed;
	}
	
	public boolean hasSetValue() {
		return setValues !=null && setValues.size() > 0;
	}
	
	public boolean hasLastSaved() {
		boolean resp = false;
		if(setValues !=null && setValues.size() > 0) {
			for(SetValue sv : setValues) {
				if(sv.value != null) {
					if(sv.value.contains("last-saved#")) {
						resp = true;
						break;
					}
				}
			}
		}
		return resp;
	}

	/*
	 * Get an expression
	 */
	public String getExpression(String in, HashMap<String, String> questionPaths, String xFormRoot) throws Exception {
		String v = in;
		
		if(xFormRoot != null) {
			v = substituteRootName(v, xFormRoot);
		}
		
		v = UtilityMethods.convertAllxlsNames(v, false, questionPaths, f_id, false, name, false);

		return v;
	}
	
	/*
	 * Get the relevance
	 *  if convertToXPath is set then any names in the format ${...} will be converted to an Xpath
	 *  By default they are converted to XLS names ${...}
	 */
	public String getRelevant(boolean convertToXPath, HashMap<String, String> questionPaths, String xFormRoot) throws Exception {
		String v = relevant;
		
		if(xFormRoot != null) {
			v = substituteRootName(v, xFormRoot);
		}
		
		if(convertToXPath) {
			v = UtilityMethods.convertAllxlsNames(v, false, questionPaths, f_id, false, name, false);
		} else {
			v = GeneralUtilityMethods.convertAllXpathNames(v, true);
		}
		return v;
	}
	
	public String getCalculate(boolean convertToXPath, HashMap<String, String> questionPaths, String xFormRoot) throws Exception {
		String v = null;
		
		v = calculate;
		
		if(xFormRoot != null) {
			v = substituteRootName(v, xFormRoot);
		}
		
		if(convertToXPath) {
			v = UtilityMethods.convertAllxlsNames(v, false, questionPaths, f_id, false, name, false);
		} else {
			v = GeneralUtilityMethods.convertAllXpathNames(v, true);
		}
		
		if(v != null && v.trim().length() == 0) {
			v = null;
		}
		
		return v;
	}
	
	public String getConstraint(boolean convertToXPath, HashMap<String, String> questionPaths, String xFormRoot) throws Exception {
		String v = constraint;
		
		if(xFormRoot != null) {
			v = substituteRootName(v, xFormRoot);
		}
		
		if(convertToXPath) {
			v = UtilityMethods.convertAllxlsNames(v, false, questionPaths, f_id, false, name, false);
		} else {
			v = GeneralUtilityMethods.convertAllXpathNames(v, true);
		}
		
		return v;
	}
	
	public String getConstraintMsg() {
		return constraint_msg;
	}
	
	public String getRequiredExpression() {
		return required_expression;
	}
	
	public String getAppearance(boolean convertToXPath, HashMap<String, String> questionPaths) throws Exception {
		
		String v = appearance;
		
		if(convertToXPath) {
			v = UtilityMethods.convertAllxlsNames(v, false, questionPaths, f_id, false, name, false);
		} else {
			v = GeneralUtilityMethods.convertAllXpathNames(v, true);
		}
		
		if(qType.equals("chart")) {
			if(v != null) {
				if(!v.contains("chart")) {
					v = v.trim() + " chart";
				}
			} else {
				v = "chart";
			}
		}
		return v;
	}
	
	public ArrayList<KeyValueSimp> getParameters() {
		ArrayList<KeyValueSimp> params = GeneralUtilityMethods.convertParametersToArray(parameters);
		if(qType.equals("parent_form") || qType.equals(SmapQuestionTypes.CHILD_FORM)) {
			params.add(new KeyValueSimp("launch_form", qType));
		}
		return params;
	}
	
	public String getPath() {
		
		if(path != null) {
			return path;		// set by xForm
		} else {
			return formRef + relativePath;	// Loaded from database
		}
	}
	
	public String getRelativePath() {
		return relativePath;
	}
	
	public String getNodesetValue() {
		return nodeset_value;
	}
	
	public String getNodesetLabel() {
		return nodeset_label;
	}
	
	public String getNodeset() {
		return nodeset;
	}
	
	public String getAutoPlay() {
		return autoplay;
	}
	
	public String getTrigger() {
		return trigger;
	}
	
	public String getAccuracy() {
		return accuracy;
	}
	
	public String getIntent() {
		return intent;
	}
	
	public ArrayList<SetValue> getSetValues() {		
		return setValues;
	}
	
	public String getSetValueArrayAsString(Gson gson) {
		if(setValues == null) {
			return null;
		}
		return gson.toJson(setValues);
	}
	
	/*
	 * Setters
	 */

	public void setId(int v) {
		q_id = v;
	}
	
	public void setSeq(int seq) {
		this.seq = seq;
	}
	
	public void setListName(String v) {
		listname = v;
	}
	
	public void setListId(int v) {
		l_id = v;
	}
	
	/*
	 * Applicable to select type questions, the list id links the question to the set of choices
	 * A list id will already be set if the question was read from the database else the question came from an xml file
	 * If no list id then
	 *   If a listname has been set for the question use the list id for that list, creating it if the list has not already been created
	 *   Else create a list based on the question name
	 */
	public void setListId(Connection sd, int sId) {
		
		if(this.l_id == 0) {		// No list has been set for this question
			
			// Create a list name if the question is a select type
			if(this.qType.startsWith("select") || this.qType.equals("rank")) {
				
				String sqlCheck = "select l_id from listname where s_id = ? and name = ?;";
				PreparedStatement pstmtCheck = null;
				
				String sql = "insert into listname (s_id, name) values(?, ?);";
				PreparedStatement pstmtCreate = null;
				
				try {
					
					// Set the list name to the question name if a list name has not already been specified
					if(listname == null) {
						listname = this.name;
					}
					
					// Get the list id for this list
					pstmtCheck = sd.prepareStatement(sqlCheck);
					pstmtCheck.setInt(1, sId);
					pstmtCheck.setString(2, listname);
					ResultSet rs = pstmtCheck.executeQuery();
					if(rs.next()) {
						this.l_id = rs.getInt(1);
					} else {
						pstmtCreate = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
						pstmtCreate.setInt(1, sId);
						pstmtCreate.setString(2, listname);
						pstmtCreate.executeUpdate();
						
						ResultSet rsCreate = pstmtCreate.getGeneratedKeys();
						rsCreate.next();
						this.l_id = rsCreate.getInt(1);
					}
			
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if(pstmtCreate != null) try {pstmtCreate.close();} catch(Exception e) {};
				}
			}
			
		} else {
			// Get the list name
			try {
				this.listname = GeneralUtilityMethods.getListName(sd, this.l_id);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}

	public void setPublished(boolean v) {
		published = v;
	}
	
	public void setName(String name) {
		this.name = name;
		this.column_name = GeneralUtilityMethods.cleanName(name, true, true, false);	// Do not remove smap meta names as they are added through this mechanism
	}
	
	public void setColumnName(String v) {	// Override column name
		column_name = v;
	}

	public void setType(String v) {
		qType = v;
	}
	
	public void setDataType(String v) {
		if(v != null) {
			if(v.equals("int") || v.equals("integer")) {
				dataType = "int";
			} else if(v.equals("decimal")) {
				dataType = v;
			}
			dataType = v;
		} else {
			v = null;
		}

	}

	public void setQuestion(String question) {
		this.question = question;
	}
	
	public void setQTextId(String value) {
		qtext_id = value;
	}

	public void setDefaultAnswer(String defaultAnswer) {
		this.defaultAnswer = defaultAnswer;
	}

	public void setInfo(String info) {
		this.info = info;
	}
	
	public void setInfoTextId(String value) {
		infotext_id = value;
	}

	public void setVisible(boolean v) {
		visible = v;
	}
	
	public void setSource(String v) {
		source = v;
	}
	
	public void setSourceParam(String v) {
		source_param = v;
	}
	
	public void setReadOnly(boolean v) {
		readOnly = v;
	}
	
	public void setReadOnlyExpression(String v) {
		readOnlyExpression = v;
	}
	
	public void setRepeatCount(boolean v) {
		repeatCount = v;
	}

	public void setMandatory(boolean v) {
		mandatory = v;
	}
	
	public void setCompressed(boolean v) {
		compressed = v;
	}
	
	public void setRelevant(String v) {
		relevant = v;
	}
	
	public void setConstraint(String v) {
		constraint = v;
	}
	
	public void setCalculate(String v) {
		calculate = v;
	}
	
	public void setConstraintMsg(String v) {
		constraint_msg = v;
	}
	
	public void setRequiredExpression(String v) {
		required_expression = v;
	}
	
	public void setAppearance(String v) {
		appearance = v;
	}
	
	public void setParameters(String v) {
		parameters = v;
	}

	public void setFormRef(String formRef) {
		this.formRef = formRef;
	}
	
	public void setBeginRef(String v) {
		this.qGroupBeginRef = v;
	}
	
	public String getFormRef() {
		return formRef;
	}
	
	public String getBeginRef() {
		return qGroupBeginRef;
	}
	
	// use only when loading from xform
	public void setPath(String v) {
		path = v;
	}
	
	public void setSetValue(Gson gson, String v) {
		if(v == null) {
			setValues = null;
		} else {
			setValues = gson.fromJson(v, new TypeToken<ArrayList<SetValue>>() {}.getType());
		}
	}
	
	/*
	 * Path within a form
	 */
	public void setRelativePath(String v) {
		relativePath = v;
	}

	public void setNodeset(String v) {
		nodeset = v;
	}
	
	public void setNodesetValue(String v) {
		nodeset_value = v;
	}
	
	public void setNodesetLabel(String v) {
		nodeset_label = v;
	}
	
	public void setAutoPlay(String v) {
		autoplay = v;
	}
	
	public void setTrigger(String v) {
		trigger = v;
	}
	
	public void setAccuracy(String v) {
		accuracy = v;
	}
	
	public void setIntent(String v) {
		intent = v;
	}
	
	/*
	 * Set the type value in the question object based on the type and subtype
	 * values received from the OSM XML file
	 */
	public void cleanseOSM() {

		if (qType != null) {
			if (qType.equals("choice")) {
				if (qSubType != null && qSubType.equals("radio")) {
					qType = "select1";
				} else {
					qType = "select";
				}
			} else if (qType.equals("singleChoice")) {
				qType = "select1";
			} else if (qType.equals("text")) {
				if (qSubType == null) {
					qType = "string";
				} else if (qSubType.equals("A")) {
					qType = "string";
				} else if (qSubType.equals("N")) {
					qType = "int";
				} else if (qSubType.equals("D")) {
					qType = "decimal";
				}
			} else if (qType.equals("date")) {
				if (qSubType == "DT") {
					qType = "dateTime";
				} else if (qSubType.equals("D")) {
					qType = "date";
				} else if (qSubType.equals("T")) {
					qType = "time";
				}
			} else {
				log.info("Unknown type in cleanseOSM: " + qType);
			}
		}
	}

	public void setFormId(int value) {
		this.f_id = value;
	}

	/*
	 * Only return choices that were not created by an external csv file
	 */
	public Collection<Option> getChoices(Connection sd) throws SQLException {
		Collection<Option> internalChoices = new ArrayList<Option> ();
		
		if (choices == null) {
			loadChoices(sd);
		}
		
		ArrayList<Option> cArray = new ArrayList<Option>(choices);
		for(int i = 0; i < cArray.size(); i++) {
			if(!cArray.get(i).getExternalFile()) {
				internalChoices.add(cArray.get(i));
			}
		}
		return internalChoices;
	}
	
	/*
	 * Add a parameter to the parameter string
	 * Replace existing parameters of the same value
	 */
	public void addParameter(String v) {
		if(v != null) {
			String[] px = v.trim().split("=");
			if(px.length == 2) {
				if(parameters == null) {
					parameters = px[0].trim() + "=" + px[1].trim();
				} else {
					String[] params = parameters.split(" ");
					boolean replaced = false;
					for(int j = 0; j  < params.length; j++) {
						if(params[j].trim().startsWith(px[0].trim() + "=")) {
							params[j] = px[0].trim() + "=" + px[1].trim();
							replaced = true;
							break;
						}
					}
					if(!replaced) {
						parameters += " " + px[0].trim() + "=" + px[1].trim();
					} else {
						parameters = "";
						for(int i = 0; i < params.length; i++) {
							if(i > 0) {
								parameters += " ";
							}
							parameters += params[i];
						}
					}
				}
			}
			
		}
	}
	
	/*
	 * Return all non external choices 
	 *   or if there is a single external choice then return all external choices
	 */
	public Collection<Option> getValidChoices(Connection sd) throws SQLException {
		
		if (choices == null) {
			loadChoices(sd);
		}
		
		Collection<Option> externalChoices = new ArrayList<Option> ();
		ArrayList<Option> cArray = new ArrayList<Option>(choices);
		boolean external = false;
		for(int i = 0; i < cArray.size(); i++) {
			if(cArray.get(i).getExternalFile()) {
				external = true;
				externalChoices.add(cArray.get(i));
			}
		}
		if(external) {
			return externalChoices;
		} else {
			return choices;
		}
	}

	public String toString() {
		StringBuffer returnBuffer = new StringBuffer();
		returnBuffer.append("q_id=" + this.getId());
		returnBuffer.append(",");
		returnBuffer.append("f_id=" + f_id);
		returnBuffer.append(",");
		returnBuffer.append("seq=" + this.getSeq());
		returnBuffer.append(",");
		returnBuffer.append("qname=" + this.getName());
		returnBuffer.append(",");
		returnBuffer.append("qtype=" + this.getType());
		returnBuffer.append(",");
		returnBuffer.append("questionId=" + this.getQTextId());
		returnBuffer.append(",");
		returnBuffer.append("defaultanswer=" + this.getDefaultAnswer());
		returnBuffer.append(",");
		returnBuffer.append("infoId=" + this.getInfoTextId());
		returnBuffer.append(",");
		returnBuffer.append("readonly=" + this.readOnly);
		return returnBuffer.toString();
	}
	
	/*
	 * Return true if the appearance includes "phoneonly" 
	 * This indicates that the survey results should not be stored on the server
	 */
	 public boolean getPhoneOnly() {
		 boolean po = false;
		 
		 if(appearance != null) {
			 if(appearance.contains("phoneonly")) {
				 po = true;
			 }
		 }
		 return po;
	 }
	 
	 public boolean isReference() {
		 boolean value = false;
		 if(parameters != null) {
			 String refValue = GeneralUtilityMethods.getSurveyParameter("ref", getParameters());
			 if(refValue != null && refValue.equals("yes")) {
				 value = true;
			 }
		 }
		 return value;
	 }
	 
	 private Collection<Option> loadChoices(Connection sd) throws SQLException {
		 if(choices == null) {
				//PersistenceContext pc = new PersistenceContext("pgsql_jpa");
				//OptionManager om = new OptionManager(pc);
				//choices = om.getByQuestionId(q_id);
				
				JdbcOptionManager om = null;
				try {
					om = new JdbcOptionManager(sd);
					choices = om.getByListId(l_id);
				} finally {
					if(om != null) {om.close();}
				}
			}
			return choices;
	 }
	 
	    /*
	     * Replace the root name from an xForm with main
	     */
	    private String substituteRootName(String in, String xFormRoot) {
	    	
	    	if(xFormRoot != null && in != null) {
	    		if(in.contains("/" + xFormRoot + "/")) {
	    			in = in.replaceAll("/" + xFormRoot + "/", "/main/");
	    		}
	    	}
	    	return in;
	    }
	    
	 
}