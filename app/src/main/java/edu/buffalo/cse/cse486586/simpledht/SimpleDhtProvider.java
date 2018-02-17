package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Map;
import java.util.TreeMap;

public class SimpleDhtProvider extends ContentProvider {
    static final String Content_URI = "content://edu.buffalo.cse.cse486586.simpledht.provider";
    static final Uri URI = Uri.parse(Content_URI);
    static final String REMOTE_PORT[] = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    static final String KEY_FIELD = "key";
    static final String VALUE_FIELD = "value";
    String successor_node = "";
    String predecessor_node = "";
    String predecessor_hash;
    String successor_hash;
    String current_port_hash;
    Map<String, String> sort = new TreeMap<String, String>();
    String myPort = "";
    String portStr;
    int portnum;


    @Override
    /* The delete function checks for three conditions : 1. if * is given as the selection parameter then delete all <key,value> pairs
    stored in entire DHT,
    2. if @ is given as the selection parameter then delete all <key,value> pairs stored in the
    local partiton of the node on which delete() was requested,
    3. if a key value is given as the selection parameter then first find where that key is stored then delete that <key,value> pair.

    The count variable stores the number of key(s) that have been deleted to check if the required number of key(s) has been deleted or not.
    This is validate the success of delete() method.
    */
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub

        Context c = this.getContext();
        String files[] = c.fileList();
        if (selection.equals("@") || selection.equals("/@/")) {
            try {
                int count = 0;
                for (String file : files) {
                    File d = getContext().getFilesDir();
                    File f = new File(d, file);
                    boolean del = f.delete();

                    if (del) {
                        count += 1;
                    }

                }
                return count;
            } catch (Exception e) {
                Log.e("Delete", "failed");
            }
        } else if (!selection.equals("@") || !selection.equals("*")) {
            if (c.deleteFile(selection)) {
                return 1;
            }
        } else {

            try {
                int count = 0;
                for (String file : files) {
                    File d = getContext().getFilesDir();
                    File f = new File(d, file);
                    boolean del = f.delete();

                    if (del) {
                        count += 1;
                    }


                }
                return count;
            } catch (Exception e) {
                Log.e("Delete", "failed");
            }

        }
        return 0;

    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    /* The task of insert() is to find out the correct partition for the key which is to be inserted and
    then store the <key,value> pair in the correct node */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        Log.d("InsertKey", values.getAsString("key"));
        try {
            FileOutputStream outputStream;
            String KEY = values.getAsString("key");
            String value = values.getAsString("value");
            String key_hash = genHash(KEY);
            //first check if the key to be inserted lies in the partition of current node on which insert request has been send then forward the request
            //in the circular way to all the other nodes and repeat the process
            if (successor_node.equals("") && predecessor_node.equals("")) {
                outputStream = getContext().openFileOutput(KEY, Context.MODE_PRIVATE);
                outputStream.write(values.getAsString("value").getBytes());
                outputStream.close();
            } else if ((key_hash.compareTo(predecessor_hash) > 0) && (key_hash.compareTo(current_port_hash) < 0)) {
                outputStream = getContext().openFileOutput(KEY, Context.MODE_PRIVATE);
                outputStream.write(value.getBytes());
                outputStream.close();
            } else if ((predecessor_hash.compareTo(current_port_hash) > 0) && (current_port_hash.compareTo(key_hash) > 0)) {
                outputStream = getContext().openFileOutput(KEY, Context.MODE_PRIVATE);
                outputStream.write(values.getAsString("value").getBytes());
                outputStream.close();
            } else if ((predecessor_hash.compareTo(current_port_hash) > 0) && (key_hash.compareTo(predecessor_hash) > 0)) {
                outputStream = getContext().openFileOutput(KEY, Context.MODE_PRIVATE);
                outputStream.write(values.getAsString("value").getBytes());
                outputStream.close();
            } else {
                String from_insert = myPort + ":" + successor_node + ":" + " " + ":" + "insert" + ":" + KEY + ":" + value + ":" + " ";
                new NodeJoinRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, from_insert);
                Log.d("insert else", from_insert);

            }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (NullPointerException n) {
            n.printStackTrace();
        }
        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    // According to the given problem statement all the node join requests are send to node with port number 5554
    public boolean onCreate() {
        // TODO Auto-generated method stub

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            e.printStackTrace();

        }
        try {
            sort.put(genHash("5554"), "5554");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        try {
            current_port_hash = genHash(portStr);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        // Sending the Join request in form of a message to the client task of initial node 5554
        if (!myPort.equals("11108")) {
            String msg = myPort + ":" + " " + ":" + " " + ":" + "JoinRequest" + ":" + " " + ":" + " " + ":" + " ";
            successor_node = "";
            predecessor_node = "";
            new NodeJoinRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
        }
        return false;
    }
    //serverQuery() is the method that facilitates "*"query as well as query for a key which is not present
    // on the originating node (on which query() was made)
    private String serverQuery(String selection, String originating_node) {
        String received = null;
        Log.d("InsideServerQuery", selection);
        try {
            String keys = "", values = "";
            if (selection.equals("*")) {
                if (!originating_node.equals(successor_node)) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(successor_node) * 2);
                    Log.d("Successor", successor_node);
                    String str = myPort + ":" + " " + ":" + " " + ":" + "query" + ":" + selection + ":" + originating_node;
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.writeUTF(str);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    received = in.readUTF();
                }

                if (received != null) {
                    String[] key_value_array = received.split(":");
                    if (key_value_array.length == 2) {
                        String[] keys_array = key_value_array[0].split("-");
                        String[] values_array = key_value_array[1].split("-");
                        for (int i = 0; i < keys_array.length; i++) {
                            keys = keys + "-" + keys_array[i];
                            values = values + "-" + values_array[i];

                        }
                    }
                }

                String[] files = getContext().fileList();
                Log.d("length of files", String.valueOf(files.length));
                String str = "";
                for (int i = 0; i < files.length; i++) {
                    Log.d("inside for", myPort);

                    FileInputStream fis = getContext().openFileInput(files[i]);
                    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                    BufferedReader br = new BufferedReader(isr);

                    str = br.readLine();


                    keys = keys + "-" + files[i];
                    values = values + "-" + str;

                    fis.close();
                    isr.close();


                }
                if (keys.length() > 0) {
                    keys = keys.substring(1);
                    values = values.substring(1);
                }
                String response = keys + ":" + values;
                Log.d("in serverquery2", myPort + " " + response);
                return response;

            } else if ((genHash(selection).compareTo(predecessor_hash) >= 0) && (genHash(selection).compareTo(current_port_hash) <= 0)) {

                String s = "";
                try {
                    FileInputStream fis = getContext().openFileInput(selection);
                    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                    BufferedReader bufferedReader = new BufferedReader(isr);
                    s = bufferedReader.readLine();
                    fis.close();
                    isr.close();
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException n) {
                    n.printStackTrace();
                }
                String key_value = selection + ":" + s;
                Log.d("type itself", myPort + s + selection);


                return key_value;
            } else if (((genHash(selection).compareTo(predecessor_hash) > 0) || (genHash(selection).compareTo(current_port_hash) < 0)) && (predecessor_hash.compareTo(current_port_hash) > 0) && (successor_hash.compareTo(current_port_hash) >= 0)) {
                String s = "";
                try {
                    FileInputStream fis = getContext().openFileInput(selection);
                    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                    BufferedReader bufferedReader = new BufferedReader(isr);
                    s = bufferedReader.readLine();
                    fis.close();
                    isr.close();
                    bufferedReader.close();


                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException n) {
                    n.printStackTrace();
                }

                String key_value = selection + ":" + s;
                Log.d("type last node", selection + " " + s);

                return key_value;
            } else {
                if (!successor_node.equals(originating_node)) {
                    String temp = successor_node;
                    //String key = "", value = "";
                    Log.d("in last while", myPort + temp);
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(temp) * 2);
                    Log.d("Successor", successor_node);
                    String str = myPort + ":" + " " + ":" + " " + ":" + "individual key query" + ":" + selection + ":" + originating_node;
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.writeUTF(str);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    received = in.readUTF();
                    return received;
                } else {
                    return null;
                }

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException n) {
            n.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return null;
    }

    /*
    For query() also three types of cases are implemented: 1. If a single "key" is given as selection parameter then the current node checks for the key
    in its local partition, if not found then it forwards the query to successive nodes in the ring, the node which has the key returns the <key,value> pair
    to the node on which request was made and it replies back with the result,
    2. If "@" is the selection parameter then the originating node on which query was made has to return all the <key,value> pairs stored on it locally
    3. If "*" query is the selection parameter then the originating node has to return all the <key,value> pairs stored in the entire DHT
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub
        Log.d("Query", selection);


        MatrixCursor cursor = new MatrixCursor(new String[]{"key", "value"});
        String originating_node = portStr;
        try {
            if (successor_node.equals("") && predecessor_node.equals("")) {
                if (selection.equals("*") || selection.equals("@")) {
                    String[] files = getContext().fileList();

                    String str = "";
                    for (int i = 0; i < files.length; i++) {
                        FileInputStream fis = getContext().openFileInput(files[i]);
                        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                        BufferedReader br = new BufferedReader(isr);
                        str = br.readLine();
                        fis.close();
                        isr.close();
                        cursor.addRow(new String[]{files[i], str});
                        Log.d("type1", " Value :" + str + " retrieved for key :" + files[i]);
                    }
                } else {
                    String s = "";
                    try {
                        FileInputStream fis = getContext().openFileInput(selection);
                        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                        BufferedReader bufferedReader = new BufferedReader(isr);
                        s = bufferedReader.readLine();
                        fis.close();
                        isr.close();
                        bufferedReader.close();


                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (NullPointerException n) {
                        n.printStackTrace();
                    }
                    cursor.addRow(new String[]{selection, s});
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    Log.d("type2", selection + s);
                }
                return cursor;
            } else if (selection.equals("@")) {
                String[] files = getContext().fileList();

                String str = "";
                for (int i = 0; i < files.length; i++) {


                    FileInputStream fis = getContext().openFileInput(files[i]);
                    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                    BufferedReader br = new BufferedReader(isr);

                    str = br.readLine();

                    fis.close();
                    isr.close();

                    cursor.addRow(new String[]{files[i], str});

                    Log.d("type@", " Value :" + str + " retrieved for key :" + files[i]);


                }
                return cursor;
            } else if (selection.equals("*")) {
                Log.d("in type *", "reached");

                String[] files = getContext().fileList();
                Log.d("FilesLength", Integer.toString(files.length));
                String str = "";
                for (int i = 0; i < files.length; i++) {


                    FileInputStream fis = getContext().openFileInput(files[i]);
                    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                    BufferedReader br = new BufferedReader(isr);

                    str = br.readLine();

                    fis.close();
                    isr.close();
                    Log.d("in type * loop1", files[i] + str);

                    cursor.addRow(new String[]{files[i], str});
                }
                String temp = successor_node;
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(temp) * 2);
                String to_server = myPort + ":" + temp + ":" + "" + ":" + "query" + ":" + "*" + ":" + originating_node;
                DataOutputStream out_of_query = new DataOutputStream(socket.getOutputStream());
                out_of_query.writeUTF(to_server);
                Log.d("sending *", myPort + " " + temp);
                DataInputStream in_query = new DataInputStream(socket.getInputStream());
                String from_server = in_query.readUTF();
                String key_values[] = from_server.split(":");
                String key_pos[] = key_values[0].split("-");
                if (key_pos.length > 0) {
                    Log.d("no.of rows in", myPort + key_pos);
                    String value_pos[] = key_values[1].split("-");
                    for (int i = 0; i < key_pos.length; i++) {
                        cursor.addRow(new String[]{key_pos[i], value_pos[i]});
                        Log.d("in type *", "in for loop");

                    }
                    socket.close();
                }
                return cursor;

            } else if ((genHash(selection).compareTo(predecessor_hash) > 0) && (genHash(selection).compareTo(current_port_hash) < 0)) {

                String s = "";
                try {
                    FileInputStream fis = getContext().openFileInput(selection);
                    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                    BufferedReader bufferedReader = new BufferedReader(isr);
                    s = bufferedReader.readLine();


                    fis.close();
                    isr.close();
                    bufferedReader.close();


                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException n) {
                    n.printStackTrace();
                }
                cursor.addRow(new String[]{selection, s});


                // cursor.setNotificationUri(getContext().getContentResolver(), uri);
                Log.d("type itself", myPort + s + selection);

                return cursor;
            } else if (((genHash(selection).compareTo(predecessor_hash) > 0) || (genHash(selection).compareTo(current_port_hash) < 0)) && (predecessor_hash.compareTo(current_port_hash) > 0) && (successor_hash.compareTo(current_port_hash) > 0)) {
                String s = "";
                try {
                    FileInputStream fis = getContext().openFileInput(selection);
                    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                    BufferedReader bufferedReader = new BufferedReader(isr);
                    s = bufferedReader.readLine();


                    fis.close();
                    isr.close();
                    bufferedReader.close();


                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException n) {
                    n.printStackTrace();
                }
                cursor.addRow(new String[]{selection, s});


                cursor.setNotificationUri(getContext().getContentResolver(), uri);
                Log.d("type last node", selection + " " + s);

                return cursor;
            } else {
                String temp = successor_node;
                //String key = "", value = "";

                Log.d("in last while", myPort + temp);
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(temp) * 2);
                Log.d("Successor", successor_node);
                String str = myPort + ":" + " " + ":" + " " + ":" + "individual key query" + ":" + selection + ":" + originating_node;
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF(str);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                String rec = in.readUTF();
                socket.close();
                String received[] = rec.split(":");

                Log.d("response", rec);
                cursor.addRow(new String[]{received[0], received[1]});

                return cursor;

            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException n) {
            n.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }


        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            while (true) {
                ServerSocket serverSocket = sockets[0];

                try {
                    Socket socket = serverSocket.accept();


                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out_of_server = new DataOutputStream(socket.getOutputStream());
                    String from_client = in.readUTF();

                    String[] msg = from_client.split(":");
                    //Once a join request is received that node is added to the ring and its successor and predecessor are computed
                    // the successor and predecessor are sent to the node in form of an Updated message string
                    if (msg[3].equals("JoinRequest")) {

                        portnum = Integer.parseInt(msg[0]) / 2;
                        sort.put(genHash(String.valueOf(portnum)), String.valueOf(portnum));
                        String[] arranger = sort.values().toArray(new String[sort.size()]);

                        for (int i = 0; i < arranger.length; i++) {

                            String successor = "";
                            String predecessor = "";
                            if (i == (arranger.length - 1)) {
                                predecessor = arranger[i - 1];
                                successor = arranger[0];
                            } else if (i == 0) {
                                predecessor = arranger[arranger.length - 1];
                                successor = arranger[i + 1];
                            } else {
                                successor = arranger[i + 1];
                                predecessor = arranger[i - 1];
                            }
                            int port = Integer.parseInt(arranger[i]) * 2;
                            String msg_to_request = String.valueOf(port) + ":" + successor + ":" + predecessor + ":" + "Updated" + ":" + " " + ":" + " " + " ";
                            publishProgress(msg_to_request);

                        }

                    }

                    else if (msg[3].equals("Replied")) {
                        successor_node = msg[1];
                        predecessor_node = msg[2];
                        predecessor_hash = genHash(predecessor_node);
                        successor_hash = genHash(successor_node);
                        out_of_server.writeUTF("Acknowledgement");
                        out_of_server.flush();
                        socket.close();
                    }

                    else if (msg[3].equals("insert")) {
                        String key = msg[4];
                        String val = msg[5];
                        Uri.Builder uriBuilder = new Uri.Builder();
                        uriBuilder.authority("edu.buffalo.cse.cse486586.simpledht.provider");
                        uriBuilder.scheme("content");
                        ContentValues cv = new ContentValues();
                        cv.put(KEY_FIELD, key);
                        cv.put(VALUE_FIELD, val);
                        Uri uri = uriBuilder.build();
                        insert(uri, cv);
                    }
                    else if (msg[3].equals("query")) {

                        String response = serverQuery(msg[4], msg[5]);
                        out_of_server.writeUTF(response);
                        out_of_server.flush();
                        socket.close();


                    }
                    else if (msg[3].equals("individual key query")) {
                        String response = serverQuery(msg[4], msg[5]);
                        out_of_server.writeUTF(response);
                        out_of_server.flush();
                        socket.close();

                    }
                    else if (msg[3].equals("send to origin")) {
                        String str = myPort + ":" + msg[4] + ":" + msg[5];
                        out_of_server.writeUTF(str);
                        out_of_server.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();

                }


            }
        }

        protected void onProgressUpdate(String... strings) {
            String strReceived = strings[0].trim();
            String to_send[] = strReceived.split(":");
            if (to_send[3].equals("Updated")) {
                new NodeJoinRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, strReceived);
            }
            if (to_send[3].equals("Replied")) {
                new NodeJoinRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, strReceived);
            }
        }
    }

    private class NodeJoinRequest extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... msgs) {
            while (true) {
                String msg = msgs[0];
                Log.d("In nodejointask", msg);
                String[] msg_break = msg.split(":");
                try {
                    if (msg_break[3].equals("JoinRequest")) {

                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(REMOTE_PORT[0]));
                        Log.d("Before server", msg);
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        out.writeUTF(msg);

                    } else if (msg_break[3].equals("Updated")) {
                        String port = msg_break[0];
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(port));
                        DataOutputStream to_server = new DataOutputStream(socket.getOutputStream());
                        String to_port = msg_break[0] + ":" + msg_break[1] + ":" + msg_break[2] + ":" + "Replied" + ":" + " " + ":" + " " + ":" + " ";
                        ;
                        to_server.writeUTF(to_port);
                        Log.d("in if 2", to_port);
                        DataInputStream from_server = new DataInputStream(socket.getInputStream());
                        String s = from_server.readUTF();
                        if (s.equals("Acknowledgement")) {
                            // socket.close();
                            // from_server.close();
                            // to_server.close();
                        }

                    } else if (msg_break[3].equals("insert")) {
                        String successor = msg_break[1];
                        String successor_port = String.valueOf(Integer.parseInt(successor) * 2);
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(successor_port));
                        DataOutputStream for_successor = new DataOutputStream(socket.getOutputStream());
                        for_successor.writeUTF(msg);
                        Log.d("Send server successor", msg);
                    }

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }
        }
    }
}
