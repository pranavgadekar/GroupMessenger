package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    private static final String TAG = GroupMessengerActivity.class.getSimpleName();
    private static ArrayList<String> REMOTE_PORT = new ArrayList<String>(){{add("11108");add("11112");add("11116");add("11120");add("11124");}};
//    private static String[] REMOTE_PORT_TEST = {"11108","11112","11116"};

    static final int SERVER_PORT = 10000;
    private Uri providerURI = Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");
    private static String KEY = "key";
    private static String VALUE = "value";
    private static int maxProposedSeqNumber = -1;
    private static int finalProposedSeqNumber = -1;

    private static PriorityBlockingQueue<MessageObject> messagesPriorityQueue;


    private static class MessageObjectComparator implements Comparator<MessageObject>{
        @Override
        public int compare(MessageObject lhs, MessageObject rhs) {
            if(lhs.getSuggestedSeqNumber()<rhs.getSuggestedSeqNumber()){
                return -1;
            }
            else if(lhs.getSuggestedSeqNumber() > rhs.getSuggestedSeqNumber()){
                return 1;
            }
            return 0;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        messagesPriorityQueue = new PriorityBlockingQueue<MessageObject>(15,new MessageObjectComparator());

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e){
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        final EditText editText = (EditText) findViewById(R.id.editText1);
        tv.append(editText.getText().toString());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        findViewById(R.id.button4).setOnClickListener(
                new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {

                        String msg = editText.getText().toString() + "\n";
                        Random r = new Random();
                        int messageID = r.nextInt();
                        MessageObject messageObjectToSend = new MessageObject(msg,messageID,-1);
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, messageObjectToSend);

//                        TextView localTextView = (TextView) findViewById(R.id.textView1);
//                        localTextView.append("\t" + msg);
//                        TextView remoteTextView = (TextView) findViewById(R.id.textView1);
//                        remoteTextView.append(msg + "\t\n");

                        editText.setText("");
                    }
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            Socket clientSocket = null;
            while(true) {


                try {
                    ServerSocket serverSocket = sockets[0];

                    while (true) {
                        clientSocket = serverSocket.accept();
                        ObjectInputStream objectInputStream = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                        MessageObject messageObjectReceived = (MessageObject) objectInputStream.readObject();

                        messagesPriorityQueue.add(messageObjectReceived);

                        int maxFromPriorityQueue = Integer.MIN_VALUE;

                        for (MessageObject messageObject : messagesPriorityQueue) {
                            if (maxFromPriorityQueue < messageObject.getSuggestedSeqNumber()) {
                                maxFromPriorityQueue = messageObject.getSuggestedSeqNumber();
                            }
                        }

                        maxProposedSeqNumber = Math.max(maxProposedSeqNumber, maxFromPriorityQueue) + 1;

                        String message = messageObjectReceived.getMessage();
                        int messageID = messageObjectReceived.messageID;

                        MessageObject messageObjectProposed = new MessageObject(message, messageID, maxProposedSeqNumber);

                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
                        objectOutputStream.writeObject(messageObjectProposed);
                        objectOutputStream.flush();

                        MessageObject messageObjectFinal = (MessageObject) objectInputStream.readObject();

                        for (MessageObject messageObject : messagesPriorityQueue) {
                            if (messageObject.messageID == messageObjectFinal.messageID) {
                                messageObject.setSuggestedSeqNumber(messageObjectFinal.getSuggestedSeqNumber());
                                messageObject.setStatus();
                            }
                        }

                        deliverMessage();

                        objectOutputStream.close();
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    //Server connection broken hence clearing the priority Queue
                    Log.e(TAG, "ServerTask IOException");
                    maxProposedSeqNumber-=1;
                    messagesPriorityQueue.clear();
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "ServerTask ClassNotFoundException");
                }
            }
//            return null;
        }

        private void deliverMessage() {
            while(!messagesPriorityQueue.isEmpty() && messagesPriorityQueue.peek().isStatus()){
                MessageObject messageObjectToDeliver = messagesPriorityQueue.peek();
                messagesPriorityQueue.poll();
                publishProgress(messageObjectToDeliver.getMessage());
                ContentValues keyValueToInsert = new ContentValues();
                String keyToInsert = String.valueOf(messageObjectToDeliver.getSuggestedSeqNumber());
                String valueToInsert = messageObjectToDeliver.getMessage();

                keyValueToInsert.put(KEY,keyToInsert);
                keyValueToInsert.put(VALUE,valueToInsert);

                getContentResolver().insert(providerURI, keyValueToInsert);

            }
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */

        }
    }

    private class ClientTask extends AsyncTask<MessageObject, Void, Void> {
        ArrayList<Socket> listOfSockets = new ArrayList<Socket>();
        ArrayList<ObjectOutputStream> listOfObjectOPStreams = new ArrayList<ObjectOutputStream>();
        ArrayList<MessageObject> listOfMessageObjects = new ArrayList<MessageObject>();
        MessageObject messageObjectToSend;
        Socket socket;
        int remotePortFailed=0;

        @Override
        protected Void doInBackground(MessageObject... msgs) {
            try {
                messageObjectToSend = msgs[0];
                String message = messageObjectToSend.getMessage();
                int messageID = messageObjectToSend.messageID;

                for(String remotePort: REMOTE_PORT) {

                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));

                        listOfSockets.add(socket);
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                        listOfObjectOPStreams.add(objectOutputStream);
                        objectOutputStream.writeObject(messageObjectToSend);
                        objectOutputStream.flush();

                        ObjectInputStream objectInputStream = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
                        MessageObject proposedMessagesReceived = (MessageObject) objectInputStream.readObject();

                        listOfMessageObjects.add(proposedMessagesReceived);
                        remotePortFailed++;

                }

                finalProposedSeqNumber = Integer.MIN_VALUE;

                for(MessageObject proposedMessages : listOfMessageObjects){
                    if(finalProposedSeqNumber<proposedMessages.getSuggestedSeqNumber()){
                        finalProposedSeqNumber = proposedMessages.getSuggestedSeqNumber();
                    }
                }

                MessageObject messageObjectFinal = new MessageObject(message,messageID,finalProposedSeqNumber);

                for(ObjectOutputStream objectOutputStream : listOfObjectOPStreams ){
                    try{
                        objectOutputStream.writeObject(messageObjectFinal);
                        objectOutputStream.flush();
                    } catch (IOException e){

                    }
                }

                for(ObjectOutputStream objectOutputStream : listOfObjectOPStreams){
                    objectOutputStream.close();
                }

                for(Socket s: listOfSockets){
                    s.close();
                }

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                REMOTE_PORT.remove(remotePortFailed);

                try {
                    Log.e(TAG, "ClientTask socket IOException");
                    for (Socket s : listOfSockets) {
                        s.close();
                    }

                    listOfSockets.clear();

                    for (ObjectOutputStream objectOutputStream : listOfObjectOPStreams) {
                        objectOutputStream.close();
                    }

                    listOfObjectOPStreams.clear();
                    listOfMessageObjects.clear();

                    for (String remotePort : REMOTE_PORT) {

                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));

                        listOfSockets.add(socket);
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                        listOfObjectOPStreams.add(objectOutputStream);
                        objectOutputStream.writeObject(messageObjectToSend);
                        objectOutputStream.flush();

                        ObjectInputStream objectInputStream = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
                        MessageObject proposedMessagesReceived = (MessageObject) objectInputStream.readObject();

                        listOfMessageObjects.add(proposedMessagesReceived);

                    }

                    finalProposedSeqNumber = Integer.MIN_VALUE;

                    for (MessageObject proposedMessages : listOfMessageObjects) {
                        if (finalProposedSeqNumber < proposedMessages.getSuggestedSeqNumber()) {
                            finalProposedSeqNumber = proposedMessages.getSuggestedSeqNumber();
                        }
                    }

                    MessageObject messageObjectFinal = new MessageObject(messageObjectToSend.getMessage(), messageObjectToSend.messageID, finalProposedSeqNumber);

                    for (ObjectOutputStream objectOutputStream : listOfObjectOPStreams) {
                        objectOutputStream.writeObject(messageObjectFinal);
                        objectOutputStream.flush();
                    }

                    for (ObjectOutputStream objectOutputStream : listOfObjectOPStreams) {
                        objectOutputStream.close();
                    }

                    for (Socket s : listOfSockets) {
                        s.close();
                    }
                } catch (UnknownHostException e1){
                    Log.e(TAG, "ClientTask socket UnknownHostException inner try-catch");
                } catch (IOException e1){
                    Log.e(TAG, "ClientTask socket IOException inner try-catch");
                } catch (ClassNotFoundException e1){
                    Log.e(TAG, "ClientTask socket ClassNotFoundException inner try-catch");
                }
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "ClientTask socket ClassNotFoundException");
            }
            listOfSockets.clear();
            listOfObjectOPStreams.clear();
            listOfMessageObjects.clear();

            return null;
        }

    }
}
