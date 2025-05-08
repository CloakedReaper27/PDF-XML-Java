package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.mustangproject.Invoice;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.mustangproject.ZUGFeRD.IZUGFeRDExporter;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;
import org.mustangproject.*;
import org.mustangproject.ZUGFeRD.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import org.json.JSONArray;

import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.lang.reflect.Method;
import java.util.Date;
import org.json.JSONException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;

public class Main {

    public static void main(String[] args) {

        ServerSocket server;
        Socket client;
        InputStream input;
        BufferedReader reader;
        OutputStream output;

        try {
            // server starts
            server = new ServerSocket(1010);
            System.out.println("Server started, waiting for connections...");

            while (true) {
                client = server.accept();
                System.out.println("Client connected: " + client.getRemoteSocketAddress());

                input = client.getInputStream();
                reader = new BufferedReader(new InputStreamReader(input));
                output = client.getOutputStream();

                // Read data from the client (Node.js)
                StringBuilder messageBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    messageBuilder.append(line);
                }

                // Parse the JSON message
                JSONObject jsonMessage = new JSONObject(messageBuilder.toString());
                String filePath = jsonMessage.getString("filePath");
                int number = jsonMessage.getInt("number");
                System.out.printf("File path to read PDF = "+filePath);
                
                
                System.out.println("\nStarting File processing");

                if (number == 0) {
                    System.out.println("\nShutting Down server");
                    break;
                    
                } else if (number == 3) {
                    
                    System.out.println("\nStarting Write XML --> PDF");
                    JSONObject pdfData = jsonMessage.getJSONObject("pdfdata");

                    // Invoice number and contract and order number
                    String invoiceNumber = pdfData.getString("RechnungNumber");
                    String ContractNo = pdfData.getString("ContractNo");
                    String OrderNo = pdfData.getString("OrderNo");

                    // Customer Contact
                    String CustomerName = pdfData.getString("CustomerName");
                    String CustomerEmail = pdfData.getString("CustomerEmail");
                    String CustomerAddress = pdfData.getString("CustomerAddress");
                    String CustomerPhone = pdfData.getString("CustomerPhone");
                    String CustomerZipcode = pdfData.getString("CustomerZipcode");
                    String CustomerStadt = pdfData.getString("CustomerStadt");
                    String CustomerCountry = pdfData.getString("CustomerCountry");

                    // Invoice Dates
                    String oldDueDate = pdfData.getString("DueDate");
                    String oldInvoiceDate = pdfData.getString("RechnungDate");
                    String oldDeliveryDate = pdfData.getString("DeliveryDate");
                    String oldbillingPeriodStart = pdfData.getString("BillingPeriodStart");
                    String oldbillingPeriodEnd = pdfData.getString("BillingPeriodEnd");


                    // Extract items and zusItems
                    JSONArray items = pdfData.getJSONArray("items");
                    JSONArray zusItems = pdfData.getJSONArray("zusItems");
                    // Dates formatting
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

                    Date dueDate = formatter.parse(oldDueDate);
                    Date invoiceDate = formatter.parse(oldInvoiceDate);
                    Date deliveryDate = formatter.parse(oldDeliveryDate);
                    Date BillingPeriodStart = formatter.parse(oldbillingPeriodStart);
                    Date BillingPeriodEnd = formatter.parse(oldbillingPeriodEnd);


                    System.out.printf("Data test :"+ invoiceNumber +" Date: "+ oldInvoiceDate+"\n");
                    System.out.printf("filepath: " + filePath);
                
                    // Process the file and number (you can adapt this logic as needed)
                    System.out.println("Writing XML to PDF file");
                    InputStream resourceStream = new FileInputStream(filePath);
                    
                    int ZuschlagTaxtester = 1;
                    try{
                        JSONObject zusItemtester = zusItems.getJSONObject(0);
                        ZuschlagTaxtester = zusItemtester.getInt("ZuschlagTax");
                    } catch (JSONException e) {

                        System.out.println("Error: ZuschlagTax is not an integer or missing. Defaulting to 0.");

                        ZuschlagTaxtester = 0; 
                    }
                    if (zusItems == null){
                        System.out.print("Empty zusItem");

                    }


                    try {
                        IZUGFeRDExporter ze = new ZUGFeRDExporterFromPDFA().load(resourceStream).setProducer("Producer").
                                setCreator(System.getProperty("Owner"));
                        Invoice inv = (new Invoice().setDueDate(dueDate).setIssueDate(invoiceDate).setDeliveryDate(deliveryDate).setDetailedDeliveryPeriod(BillingPeriodStart,BillingPeriodEnd)).setNumber(invoiceNumber).setBuyerOrderReferencedDocumentID(OrderNo).setContractReferencedDocument(ContractNo)
                                .setSender(new TradeParty("Test Firma GmbH", "Street", "ZipCode", "Stadt", "Country").setTaxID("TaxNumber").setVATID("VATID").setContact(new org.mustangproject.Contact("Me", "My number", "Email")).addBankDetails(new org.mustangproject.BankDetails("Bank details", "Bank code")).setEmail("Email"))
                                        .setRecipient(new TradeParty(CustomerName, CustomerAddress, CustomerZipcode, CustomerStadt, CustomerCountry)
                                                .setContact(new Contact(CustomerName, "+"+CustomerPhone, CustomerEmail)));

                        
                        
                        // loops around Items
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            String productName = item.getString("name");
                            String productdescription = item.getString("description");
                            int quantity = item.getInt("quantity");
                            double price = item.getDouble("price");
                            int tax = item.getInt("tax");

                            // Add item to ZUGFeRD invoice
                            Product product = new Product(productName, productdescription, "C62", new BigDecimal(tax));
                            Item invoiceItem = new Item(product, new BigDecimal(quantity), new BigDecimal(price));
                            inv.addItem(invoiceItem);
                        }
                        
                        // Checks if Zuschlag is empty
                        if (ZuschlagTaxtester > 1){
                            // Loops around Zuschlag
                            for (int i = 0; i < zusItems.length(); i++) {
                                JSONObject zusItem = zusItems.getJSONObject(i);
                                String zuschlagName = zusItem.getString("ZuschlagDescription");
                                int ZuschlagTax = zusItem.getInt("ZuschlagTax");
                                double ZuschlagTotal = zusItem.getDouble("ZuschlagTotal");

                                // Add zusItem to ZUGFeRD invoice
                                Product product = new Product(zuschlagName, "Zuschlag", "C62", new BigDecimal(ZuschlagTax));
                                Item invoiceItem = new Item(product, new BigDecimal(1), new BigDecimal(ZuschlagTotal));
                                inv.addItem(invoiceItem);
                            }
                        }
                        ze.setTransaction(inv);
                        ze.export(filePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    resourceStream.close();
                    System.out.println("XML Applied\nClient connection closed.");
                    client.close();

                }else if (number == 2) {
                    processFile(filePath, number);

                    // Send a response back to Node.js
                    // Send the response back to the client (Node.js)

                    // Close the client connection
                    client.close();
                    System.out.println("Client connection closed.");
                };
            }
            System.out.println("Server Disconnected");
            server.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Processes the JSON file from Node.js
    private static void processFile(String filePath, int number) {
        // Simulate file reading and processing

        try {
            if (filePath.isEmpty()) {

                System.out.println("Resource not found: ");

            }

//              InputStream resourceStream = Main.class.getClassLoader().getResourceAsStream(filePath);
                InputStream resourceStream = new FileInputStream(filePath);
                System.out.println("Reading PDF file");
                pdfReader(resourceStream);
                resourceStream.close();


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void pdfReader(InputStream pdffile) throws IOException, XPathExpressionException, ParseException {
        System.out.println("Starting Mustang Project Read " + pdffile);
        ZUGFeRDInvoiceImporter zii = new ZUGFeRDInvoiceImporter(pdffile);
        Invoice invoice = zii.extractInvoice();
        jsonPackager(invoice);

    }

    public static void jsonPackager(Invoice invoice) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = new HashMap<>();
        Method[] methods = invoice.getClass().getDeclaredMethods();

        // Loop through all methods
        for (Method method : methods) {
            // Only consider methods that start with "get" (i.e., getters)
            if (method.getName().startsWith("get")) {
                try {
                    // Invoke the getter method and get the value
                    Object value = method.invoke(invoice);

                    // If the value is a nested object, recurse into it
//                    if (value != null && !isPrimitiveOrString(value)) {
//                        value = processNestedObject(value);
//                    }
                    // Create a key based on the method name (remove "get" and make the first letter lowercase)
                    String key = method.getName().substring(3);
                    key = key.substring(0, 1).toLowerCase() + key.substring(1);

                    // Add the key and value to the nested JSON object
                    jsonMap.put(key, value);
                } catch (Exception e) {
                    System.out.println("Error invoking method " + method.getName() + ": " + e.getMessage());
                }
            }
        }

        objectMapper.writeValue(new File("C:\\Users\\Fnz\\Desktop\\Washing-service-App\\output\\Invoice_Json\\mydata.json"), jsonMap);
        System.out.println("PDF file read.... JSON file made");
    }

    private static boolean isPrimitiveOrString(Object value) {
        return value instanceof String || value.getClass().isPrimitive();
    }

    // Recursively process nested objects by reflecting over their methods
    private static Object processNestedObject(Object nestedObject) throws Exception {
        JSONObject nestedJson = new JSONObject();

        // Get all methods of the nested object class
        Method[] methods = nestedObject.getClass().getDeclaredMethods();

        // Loop through all methods of the nested object
        for (Method method : methods) {
            // Only consider methods that start with "get" (i.e., getters)
            if (method.getName().startsWith("get")) {
                try {
                    // Invoke the getter method and get the value
                    Object value = method.invoke(nestedObject);

                    // If the value is a nested object, recurse into it
                    if (value != null && !isPrimitiveOrString(value)) {
                        value = processNestedObject(value);
                    }

                    // Create a key based on the method name (remove "get" and make the first letter lowercase)
                    String key = method.getName().substring(3);
                    key = key.substring(0, 1).toLowerCase() + key.substring(1);

                    // Add the key and value to the nested JSON object
                    nestedJson.put(key, value);
                } catch (Exception e) {
                    System.out.println("Error invoking method " + method.getName() + ": " + e.getMessage());
                }
            }
        }

        return nestedJson;
    }
}
