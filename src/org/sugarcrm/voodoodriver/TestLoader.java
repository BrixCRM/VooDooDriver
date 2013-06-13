/*
 * Copyright 2011-2012 SugarCRM Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License.  You
 * may may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  Please see the License for the specific language
 * governing permissions and limitations under the License.
 */

package org.sugarcrm.voodoodriver;

import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * This class reads a VooDooDriver/Soda test script and converts it
 * into a {@link Events} class.
 *
 * @author trampus
 */

public class TestLoader {

   private Document doc = null;
   private ElementsList types = null;
   private EventLoader sodaTypes = null;
   private Events events = null;
   private Reporter reporter = null;

   /**
    * Initialize a TestLoader object using the provided soda test script.
    *
    * @param sodaTest full path to a soda test file
    * @param reporter {@link Reporter} object for logging messages and errors
    */

   public TestLoader(File sodaTest, Reporter reporter) {
      DocumentBuilderFactory dbf = null;
      DocumentBuilder db = null;

      this.reporter = reporter;

      try {
         dbf = DocumentBuilderFactory.newInstance();
         db = dbf.newDocumentBuilder();
         doc = db.parse(sodaTest);
         sodaTypes = new EventLoader();
         types = sodaTypes.getTypes();
         events = this.parse(doc.getDocumentElement().getChildNodes());
      } catch (Exception exp) {
         this.events = null;
         if (this.reporter == null) {
            System.err.println("(!)Error initializing TestLoader:");
            exp.printStackTrace();
         } else {
            this.reporter.ReportException(exp);
         }
      }
   }

   /**
    * Accessor for the {@link Events} object created from the soda test script.
    *
    * @return Events object
    */

   public Events getEvents() {
      return this.events;
   }

   /**
    * Get type information for the specified {@link Elements}
    *
    * @param elementType {@link Elements} type
    * @return a {@link VDDHash} containing the type information requested
    */

   private VDDHash getSodaElement(Elements elementType) {
      for (int k = 0; k < types.size(); k++) {
         VDDHash type = types.get(k);
         if (type.get("type") == elementType) {
            return type;
         }
      }

      assert false: "Not reached";
      return null;
   }

   /**
    * Find an accessor for a {@link Elements} object.
    *
    * Verify that the accessor used in the Soda test script is
    * valid for that Soda element.  If it is valid, the accessor
    * is returned.  If it is not, null is returned.
    *
    * @param sodaElement element to search
    * @param accessor    the accessor to search for
    * @return The accessor if the element contains it, otherwise null.
    */

   private String findElementAccessor(Elements sodaElement, String accessor) {
      String result = null;
      VDDHash foundType = null;
      VDDHash accessors = null;

      foundType = getSodaElement(sodaElement);

      if (!foundType.containsKey("accessor_attributes")) {
         return null;
      }

      accessors = (VDDHash)foundType.get("accessor_attributes");
      if (accessors.containsKey(accessor)) {
         result = accessor;
      }

      return result;
   }

   /**
    * Populate a {@link VDDHash} for a {@link Elements} with its attributes
    *
    * @param map soda element
    * @param node {@link Node} from <code>Elements.xml</code>
    * @return {@link VDDHash} object populated with that Node's attributes
    */

   private VDDHash processAttributes(VDDHash map, Node node) {
      String found_index = null;
      String accessor = null;

      if (node.hasAttributes()) {
         for (int i = 0; i < node.getAttributes().getLength(); i++) {
            Node attr = node.getAttributes().item(i);
            String name = attr.getNodeName();
            String value = attr.getNodeValue();

            if (name == "index") {
               found_index = "index";
            }

            if (accessor == null || accessor == "index") {
               accessor = findElementAccessor((Elements)map.get("type"), name);
            }

            map.put(name, value);
         }

         if (accessor != null) {
            map.put("how", accessor);
         } else if (accessor == null && found_index != null) {
            map.put("how", found_index);
         }
      }

      /*
       * Fill out HTML tag/type information, if applicable.
       */
      VDDHash type = getSodaElement((Elements)map.get("type"));
      String htmlAttrs[] = {"html_tag", "html_type"};
      for (String attr: htmlAttrs) {
         if (type.get(attr) != null) {
            map.put(attr, type.get(attr));
         }
      }

      return map;
   }

   /**
    * Check whether the actions specified are valid for this event
    *
    * @param node  the event {@link Node} to check
    * @return true if the actions are valid, false otherwise
    */

   private boolean checkActions(Node node) {
      VDDHash type = getSodaElement(Elements.valueOf(node.getNodeName().toUpperCase()));
      VDDHash sodaAttributes = (VDDHash)type.get("soda_attributes");
      VDDHash accessorAttributes = (VDDHash)type.get("accessor_attributes");

      if (node.hasAttributes()) {
         for (int i = 0; i < node.getAttributes().getLength(); i++) {
            String attr = node.getAttributes().item(i).getNodeName();
            
            if (!(sodaAttributes != null && sodaAttributes.containsKey(attr)) &&
                !(accessorAttributes != null && accessorAttributes.containsKey(attr))) {
               String err = String.format("Error: Invalid attribute for %s event: '%s'",
                                          node.getNodeName().toUpperCase(), attr);
               if (this.reporter == null) {
                  System.out.println("(!)" + err);
               } else {
                  this.reporter.ReportError(err);
               }
               return false;
            }

         }
      }

      return true;
   }

   /**
    * Parse XML Nodes from Soda test script.
    *
    * @param nodes  NodeList from the soda xml test.
    * @return Events object with Soda test script events
    */

   private Events parse(NodeList nodes) {
      VDDHash data = null;
      Events dataList = null;

      dataList = new Events();

      for (int i = 0; i < nodes.getLength(); i++) {
         Node child = nodes.item(i);
         String name = child.getNodeName();

         if (name.startsWith("#")) {
            continue;
         }

         if (!sodaTypes.isValid(name)) {
            if (this.reporter == null) {
               System.err.printf("Error: Invalid Soda Element: '%s'!\n", name);
            } else {
               this.reporter.ReportError(String.format("Error: Invalid Soda Element: '%s'!", name));
            }
            return null;
         }

         data = new VDDHash();
         data.put("do", name);
         data.put("type", Elements.valueOf(name.toUpperCase()));

         if (child.hasAttributes()) {
            data = processAttributes(data, child);
         }

         if (!checkActions(child)) {
            return null;
         }

         if (name.contains("javascript")) {
            String tmp = child.getTextContent();
            if (!tmp.isEmpty()) {
               data.put("content", tmp);
            }
         }

         if (name.contains("whitelist")) {
            String tmp = child.getTextContent();
            if (!tmp.isEmpty()) {
               data.put("content", tmp);
            }
         }

         if (child.hasChildNodes()) {
            if (name.contains("execute") || name.contains("javaplugin")) {
               String[] list = processArgs(child.getChildNodes());
               data.put("args", list);
            } else {
               Events tmp = parse(child.getChildNodes());
               if (tmp == null) {
                  return null;
               }
               data.put("children", tmp);
            }
         }

         if (!data.isEmpty()) {
            dataList.add(data);
         } else {
            System.out.printf("Note: No data found.\n");
         }
      }

      return dataList;
   }

   /**
    * Process the argument list for &lt;execute&gt; and &lt;javaplugin&gt; event Nodes.
    *
    * @param nodes  the argument list as an XML NodeList
    * @return String array of those arguments
    */

   private String[] processArgs(NodeList nodes) {
      int len = nodes.getLength() -1;
      String[] list;
      int arg_count = 0;
      int current = 0;

      for (int i = 0; i <= len; i++) {
         String name = nodes.item(i).getNodeName();
         if (name.contains("arg")) {
            arg_count += 1;
         }
      }

      list = new String[arg_count];

      for (int i = 0; i <= len; i++) {
         String name = nodes.item(i).getNodeName();
         if (name.contains("arg")) {
            String value = nodes.item(i).getTextContent();
            list[current] = value;
            current += 1;
         }
      }

      return list;
   }

}
