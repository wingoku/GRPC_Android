package com.wingoku.bidirect.client;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.wingoku.bidirect.Request;
import com.wingoku.bidirect.Response;
import com.wingoku.bidirect.WingokuServiceGrpc;
import com.wingoku.bidirect.hellogrpc.R;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;


/**
 * This Fragment displays UI to handle communication with the bundled GRPC server.
 *
 * The user may enter server host/IP of the server and execute requests while examining the log
 * file.
 *
 * In a real implementation communication wouldn't be done here, but in a dedicated controller/job
 * class.
 */
public class MainActivityFragment extends Fragment {
    @BindView(R.id.main_edit_server_host)
    EditText mServerHostEditText;

    @BindView(R.id.main_edit_server_port)
    EditText mServerPortEditText;

    @BindView(R.id.connection_numbers_ET)
    EditText mConnectionTimesET;

    @BindView(R.id.main_text_log)
    TextView mLogText;

    @BindView(R.id.main_button_send_request)
    Button mOpenChannelButton;

    @BindView(R.id.button_disconnect)
    Button mDisconnectButton;

    @BindView(R.id.button_reconnect)
    Button mReconnectButton;

    //this list contains difference between connectionRequestTime and connectionEstablishedTime for N times
    private ArrayList<Long> timeDifferenceList;

//    private ChannelImpl mChannel;
    private String mHost = "";
    private int mPort = -1;

    public MainActivityFragment () {
        Log.e("wingoku", "constructor");
        timeDifferenceList = new ArrayList<>();
    }

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState ) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        ButterKnife.bind(this, view);
//
//        makes log text view scroll automatically as new lines are added, just works in combination
//        with gravity:bottom

        mLogText.setMovementMethod(new ScrollingMovementMethod());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.e("wingoku", "onResume: benchCycles: "+ numberOfTimesToRunBenchmark);
    }

    @Override
    public void onDestroyView () {
        super.onDestroyView();
        shutdownChannel();
    }

    TimerTask timerTaskObj;
    int mTimesBenchmarkRanSofar;
    int numberOfTimesToRunBenchmark;
    @OnClick(R.id.button_start_benchmark)
    public void benchmarkGRPCConnection() {
        numberOfTimesToRunBenchmark = Integer.valueOf(mConnectionTimesET.getText().toString());
        mTimesBenchmarkRanSofar = 0;
        Timer timerObj = new Timer();
        timerTaskObj = new TimerTask() {
            public void run() {
                if(numberOfTimesToRunBenchmark<mTimesBenchmarkRanSofar)
                    stopBenchmark();

                mTimesBenchmarkRanSofar++;
                Log.e("wingokuBenchMark", "Benchmark cycles: "+ mTimesBenchmarkRanSofar);
                //perform your action here
                if(channel != null)
                    disconnectFromServerBenchmark();
                reOpenChannelFromServerBenchmark();
            }
        };
        timerObj.scheduleAtFixedRate(timerTaskObj, 0, 1000);
    }

    String benchResult;
    private void stopBenchmark() {
        timerTaskObj.cancel();

        long sum=0;
        for(long diff : timeDifferenceList) {
            sum+=diff;
        }
        float averageTime = (sum*1.0f/timeDifferenceList.size()*1.0f);
        benchResult = "AverageTime took to establish "+timeDifferenceList.size()+" connections with server: "+ averageTime;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogText.setText(benchResult);
            }
        });
    }

    ManagedChannel channel;
    StreamObserver<Request> requestStreamObserver;
    WingokuServiceGrpc.WingokuServiceStub asyncStub;
    long connectionRequestTime;// in millisecond
    long connectionEstablishedTime; // in millisecond

    @OnClick(R.id.main_button_send_request)
    public void sendRequestToServer () {
//        new SendHelloTask().execute();
        Log.e("MainActivityFragment", "Send request to server");
//        onPreExecute();
        if(channel == null) {
//            mDisconnectButton.setEnabled(true);
            connectionRequestTime = System.currentTimeMillis();
//            Log.e("wingoku", "time in milliseconds: "+ System.currentTimeMillis());
//            Log.e("MainActivityFragment", "Client requests server for connection at time (milli seconds): "+ connectionRequestTime);
            channel = ManagedChannelBuilder.forAddress(mServerHostEditText.getText().toString(), Integer.valueOf(mServerPortEditText.getText().toString())).usePlaintext(true).build();
//            ConnectivityState connectivityState = channel.getState(false);
//            Log.e("wingoku", "ConnectivityState: "+connectivityState.name());
            pollWhenClientConnectsToServer();
            /*channel.notifyWhenStateChanged(connectivityState, new Runnable() {
                @Override
                public void run() {
                    Log.e("wingoku", "onNotifyWhenStateChanged::ConnectivityState: "+channel.getState(false).name());
                    long connectivityChangeTime = System.currentTimeMillis();
                    Log.e("MainActivityFragment", "connectivity changed at time (msec): "+ System.currentTimeMillis()+ " (connectivityChangeTime - startTime): "+ (connectivityChangeTime-startingTime));
                }
            });*/
//            mOpenChannelButton.setEnabled(false);
//            Log.e("MainActivityFragment", "time Taken (milli seconds) to establish Connection: "+ (System.currentTimeMillis() - connectionRequestTime));
            sendMessageToServer();

//            Log.e("MainActivityFragment", "awaiting termination in 500 seconds");
            // necessary in command line apps otherwise client terminates instantly before the requests made above go out on the network
            /*try {
                channel.awaitTermination(500, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/
//            Log.e("MainActivityFragment", "after executing channelAwaitTerminiation");
//            requestStreamObserver.onCompleted();
        }
    }

    private void pollWhenClientConnectsToServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (channel != null) {
                        ConnectivityState state = channel.getState(true);
                        if (ConnectivityState.READY == ConnectivityState.valueOf(state.name())) {
                            connectionEstablishedTime = System.currentTimeMillis();
//                        Log.e("wingoku", "ConnectivityState = "+state.name()+"/CONNECTED!! at time(msec): "+ connectionEstablishedTime);
                            long diff = connectionEstablishedTime - connectionRequestTime;
                            Log.e("wingokuBenchMark", "connectionRequestTime: " + connectionRequestTime + " | connectionEstablishedTime: " + connectionEstablishedTime + " | difference: " + diff);
                            timeDifferenceList.add(diff);
                            break;
                        }
                    }
                }
                catch (Exception e) {
                    Log.e("wingoku", e.getLocalizedMessage());
                }
            }
        }).start();
    }

    private void initStubAndObserver() {
        asyncStub = WingokuServiceGrpc.newStub(channel);
        requestStreamObserver = asyncStub.messages(new StreamObserver<Response>() {
            @Override
            public void onNext(Response response) { //similar to onNext on java side
                Log.e("MainActivityFragment", "Client onNext");
                Log.e("MainActivityFragment", "Response from server: (" + response.getResponseMessage() + ") System.currentTimeMillis: " + System.currentTimeMillis());
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("MainActivityFragment", "Client onError");
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                Log.e("MainActivityFragment", "Client OnComplete");
            }
        });
    }

    private void sendMessageToServer() {
        if(asyncStub == null && requestStreamObserver == null) {
            initStubAndObserver();
        }
        requestStreamObserver.onNext(Request.newBuilder().setRequestMessage("message from client").build());
    }

    @OnClick(R.id.button_reconnect)
    public void onReconnectToServer(View v) {
        requestStreamObserver.onCompleted();
        if(channel != null) {
            initStubAndObserver();
        }
        mReconnectButton.setEnabled(false);
    }

    private void disconnectFromServerBenchmark() {
        requestStreamObserver.onCompleted();
        try {
            channel.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        channel.shutdown();
        asyncStub = null;
        requestStreamObserver = null;
        channel = null;
    }

    private void reOpenChannelFromServerBenchmark() {
        sendRequestToServer();
    }

    @OnClick(R.id.button_disconnect)
    public void onDisconnectFromServer(View v) {
        requestStreamObserver.onCompleted();
        try {
            channel.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        channel.shutdown();
        mDisconnectButton.setEnabled(false);
        mReconnectButton.setEnabled(true);
        asyncStub = null;
        requestStreamObserver = null;
        channel = null;
    }

    private void onPreExecute() {
//        mSendButton.setEnabled(false);

        String newHost = mServerHostEditText.getText().toString();
        if (!mHost.equals(newHost)) {
            mHost = newHost;
            shutdownChannel();
        }
        if (TextUtils.isEmpty(mHost)) {
            log("ERROR: empty host name!");
//            cancel(true);
            return;
        }

        String portString = mServerPortEditText.getText().toString();
        if (TextUtils.isEmpty(portString)) {
            log("ERROR: empty port");
//            cancel(true);
            return;
        }

        try {
            int newPort = Integer.parseInt(portString);
            if (mPort != newPort) {
                mPort = newPort;
                shutdownChannel();
            }
        } catch (NumberFormatException ex) {
            log("ERROR: invalid port");
//            cancel(true);
            return;
        }

        log("Sending hello to server...");
    }

    private void log ( String logMessage ) {
        mLogText.append("\n" + logMessage);
    }

    private void shutdownChannel () {/*
        if (mChannel != null) {
            try {
                mChannel.shutdown().awaitTerminated(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // FIXME this call seems fishy as it interrupts the main thread
                Thread.currentThread().interrupt();// NOT A GOOD PRACTICE BUT A TEMPORARY WORKAROUND
            }
        }
        mChannel = null;*/
    }

    /*private class SendHelloTask extends AsyncTask<Void, Void, String> {

        private String mHost = "";
        private int mPort = -1;

        @Override
        protected void onPreExecute () {
            mSendButton.setEnabled(false);

            String newHost = mServerHostEditText.getText().toString();
            if (!mHost.equals(newHost)) {
                mHost = newHost;
                shutdownChannel();
            }
            if (TextUtils.isEmpty(mHost)) {
                log("ERROR: empty host name!");
                cancel(true);
                return;
            }

            String portString = mServerPortEditText.getText().toString();
            if (TextUtils.isEmpty(portString)) {
                log("ERROR: empty port");
                cancel(true);
                return;
            }

            try {
                int newPort = Integer.parseInt(portString);
                if (mPort != newPort) {
                    mPort = newPort;
                    shutdownChannel();
                }
            } catch (NumberFormatException ex) {
                log("ERROR: invalid port");
                cancel(true);
                return;
            }

            log("Sending hello to server...");
        }

        @Override
        protected String doInBackground ( Void... params ) {
            try {
                if (mChannel == null) {
                    mChannel = OkHttpChannelBuilder.forAddress(mHost, mPort).build();
                }
                WingokuServiceGrpc.WingokuServiceStub greeterStub =  WingokuServiceGrpc.newStub(mChannel);
                Request helloRequest = Request.newBuilder().setRequestMessage("Android").build();

                Response helloResponse = greeterStub.(helloRequest);
                return "SERVER: " + helloResponse.getGreeting();
            } catch ( SecurityException | UncheckedExecutionException e ) {
                e.printStackTrace();
                return "ERROR: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute ( String s ) {
            shutdownChannel();
            log(s);
            mSendButton.setEnabled(true);
        }

        @Override
        protected void onCancelled () {
            mSendButton.setEnabled(true);
        }
    }*/
}
